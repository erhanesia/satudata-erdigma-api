package id.co.erdigma.satudata.config;

import java.util.HashSet;
import java.util.Set;

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
import id.co.erdigma.satudata.enums.HrisPermissionLevel;
import id.co.erdigma.satudata.enums.Role;
import id.co.erdigma.satudata.modules.user.port.EmployeeDirectory;

import lombok.extern.slf4j.Slf4j;

/**
 * Menerjemahkan token Cognito menjadi authority Spring Security.
 * Klaim {@code sub} dicocokkan ke {@code users.cognito_id}, sama seperti
 * hris-api.
 */
@Component
@Slf4j
public class CustomJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final EmployeeDirectory employeeDirectory;

    public CustomJwtAuthenticationConverter(EmployeeDirectory employeeDirectory) {
        this.employeeDirectory = employeeDirectory;
    }

    @Override
    // BUKAN readOnly = true. Method ini memicu provisioning: findByCognitoId
    // dua-argumen menjalankan HrisEmployeeDirectory.upsert(), yang menyimpan
    // baris baru. readOnly = true di sini membuat propagation REQUIRED milik
    // upsert() ikut bergabung ke transaksi read-only ini — Spring tidak pernah
    // menaikkan transaksi read-only jadi read-write — sehingga INSERT-nya
    // tidak pernah benar-benar commit (FlushMode.MANUAL / JDBC read-only) dan
    // baris "tersimpan" hanya di memori. Efeknya: updatedAt tidak pernah ada,
    // masihSegar() selalu false, dan hris-api dipanggil ulang di SETIAP
    // permintaan, selamanya. Jangan kembalikan readOnly = true di sini.
    @Transactional
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

        // Dua tingkat admin. Yang perannya ADMIN karena ditunjuk manusia tetap
        // memegang ROLE_ADMIN, tetapi hanya yang tingkat izin HRIS-nya juga
        // ADMIN yang boleh membuka manajemen pengguna — kalau tidak, admin
        // tunjukan bisa menunjuk admin baru dan gerbang ini tidak berarti apa
        // pun. Konjungsi, bukan salah satu: admin HRIS yang diturunkan lewat
        // override ikut kehilangan akses panel.
        if (user.getRole() == Role.ADMIN
                && user.getHrisPermissionLevel() == HrisPermissionLevel.ADMIN) {
            authorities.add(new SimpleGrantedAuthority("ROLE_HRIS_ADMIN"));
        }

        return new JwtAuthenticationToken(jwt, authorities, userId);
    }
}
