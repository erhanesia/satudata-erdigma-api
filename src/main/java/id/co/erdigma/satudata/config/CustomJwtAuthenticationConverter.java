package id.co.erdigma.satudata.config;

import java.util.HashSet;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.modules.user.port.EmployeeDirectory;

import lombok.extern.slf4j.Slf4j;

/**
 * Menerjemahkan token Cognito menjadi authority Spring Security.
 * Klaim {@code sub} dicocokkan ke {@code users.cognito_id}, sama seperti
 * hris-api. Hanya aktif di luar profil auth-dummy.
 */
@Component
@Profile("!auth-dummy")
@Slf4j
public class CustomJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final EmployeeDirectory employeeDirectory;

    public CustomJwtAuthenticationConverter(EmployeeDirectory employeeDirectory) {
        this.employeeDirectory = employeeDirectory;
    }

    @Override
    @Transactional(readOnly = true)
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
        String userId = jwt.getSubject();

        // Token mentah ikut diteruskan: implementasi HRIS memakainya untuk
        // menanyakan identitas orang ini ke hris-api atas namanya sendiri.
        // SecurityContextHolder belum terisi di titik ini — converter justru
        // yang sedang membangunnya — jadi Jwt inilah satu-satunya sumbernya.
        User user = employeeDirectory.findByCognitoId(userId, jwt.getTokenValue())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Set<GrantedAuthority> authorities = new HashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

        return new JwtAuthenticationToken(jwt, authorities, userId);
    }
}
