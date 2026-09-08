package id.co.erdigma.satudata.modules.user.controller;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
// Paket `authorization`, bukan `access` — inilah yang dipakai Spring Security
// versi ini dan yang sudah ditangani GlobalExceptionHandler jadi 403.
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest(properties = {
        "spring.profiles.active=dev",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.invalid/.well-known/jwks.json",
})
class UserAdminAuthorizationTest {

    @Autowired
    private UserAdminController controller;

    @AfterEach
    void bersihkanKonteksKeamanan() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("admin tunjukan (ROLE_ADMIN saja) ditolak membuka daftar pengguna")
    void adminTunjukanDitolak() {
        masuk("ROLE_ADMIN");

        assertThatThrownBy(() -> controller.index(null, 0, 20))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    @DisplayName("admin warisan HRIS diizinkan membuka daftar pengguna")
    void adminWarisanDiizinkan() {
        masuk("ROLE_ADMIN", "ROLE_HRIS_ADMIN");

        assertThatCode(() -> controller.index(null, 0, 20)).doesNotThrowAnyException();
    }

    private static void masuk(String... authorities) {
        var granted = List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("uji", "n/a", granted));
    }
}
