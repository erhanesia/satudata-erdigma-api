package id.co.erdigma.satudata.config;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import id.co.erdigma.satudata.modules.user.port.EmployeeDirectory;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Meniru principal Cognito dari header HTTP supaya seluruh aplikasi bisa jalan
 * tanpa Cognito. Polanya diambil dari hris-api
 * {@code src/test/java/com/powerhouze/hris/security/MockJwtAuthFilter.java} —
 * bedanya di sana test-only, di sini aktif lewat profil {@code auth-dummy}.
 *
 * Ganti user dengan header {@code X-Dummy-Cognito-Sub}
 * (nilai: dummy-admin, dummy-staff, dummy-resigned).
 */
@Component
@Profile("auth-dummy")
public class DummyAuthFilter extends OncePerRequestFilter {

    public static final String COGNITO_SUB_HEADER = "X-Dummy-Cognito-Sub";
    public static final String COGNITO_USERNAME_HEADER = "X-Dummy-Cognito-Username";

    private final EmployeeDirectory employeeDirectory;

    public DummyAuthFilter(EmployeeDirectory employeeDirectory) {
        this.employeeDirectory = employeeDirectory;
    }

    /**
     * Mengambil peran dari tabel users, persis seperti
     * {@code CustomJwtAuthenticationConverter} melakukannya untuk token Cognito.
     *
     * Sebelumnya filter ini memberi {@code Collections.emptyList()}, sehingga
     * setiap {@code @PreAuthorize} akan menolak SELURUH user dummy — jalur dev
     * dan jalur produksi diam-diam berperilaku berbeda. Selama belum ada
     * endpoint yang menegakkan peran, selisih itu tidak terlihat; begitu
     * endpoint unggah dijaga peran, ia langsung jadi penghalang.
     *
     * Sub yang tidak dikenal dibiarkan tanpa authority, bukan ditolak di sini —
     * penolakannya tetap tugas CurrentUserService supaya pesannya seragam.
     */
    private Collection<GrantedAuthority> resolveAuthorities(String sub) {
        return employeeDirectory.findByCognitoId(sub)
                .map(user -> List.<GrantedAuthority>of(
                        new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .orElse(List.of());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            String sub = request.getHeader(COGNITO_SUB_HEADER);
            if (sub != null && !sub.isBlank()) {
                String username = request.getHeader(COGNITO_USERNAME_HEADER);
                if (username == null || username.isBlank()) {
                    username = sub;
                }

                Jwt jwt = Jwt.withTokenValue("dummy-token")
                        .header("alg", "none")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .claim("sub", sub)
                        .claim("username", username)
                        .build();

                SecurityContextHolder.getContext()
                        .setAuthentication(new JwtPrincipalAuthentication(jwt, resolveAuthorities(sub)));
            }
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Token autentikasi yang {@link Authentication#getPrincipal()}-nya
     * mengembalikan objek {@link Jwt} mentah — kontrak yang diasumsikan
     * {@code CurrentUserArgumentResolver}.
     */
    private static final class JwtPrincipalAuthentication extends AbstractAuthenticationToken {
        private final Jwt jwt;

        JwtPrincipalAuthentication(Jwt jwt, Collection<GrantedAuthority> authorities) {
            super(authorities == null ? Collections.emptyList() : authorities);
            this.jwt = jwt;
            setAuthenticated(true);
        }

        @Override
        public Object getPrincipal() {
            return jwt;
        }

        @Override
        public Object getCredentials() {
            return jwt;
        }

        @Override
        public String getName() {
            return jwt.getSubject();
        }
    }
}
