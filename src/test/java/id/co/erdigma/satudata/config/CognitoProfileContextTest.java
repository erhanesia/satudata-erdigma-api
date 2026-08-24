package id.co.erdigma.satudata.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import id.co.erdigma.satudata.modules.user.port.EmployeeDirectory;
import id.co.erdigma.satudata.modules.user.port.hris.HrisEmployeeDirectory;

/**
 * Profil tanpa auth-dummy harus bisa start. Sebelum HrisEmployeeDirectory ada,
 * konteks gagal dibangun karena CustomJwtAuthenticationConverter meminta bean
 * EmployeeDirectory yang satu-satunya implementasinya @Profile("auth-dummy").
 *
 * jwk-set-uri sengaja ditimpa dengan host yang tidak ada: dekoder JWT yang
 * dibangun dari jwk-set-uri mengambil kuncinya saat token pertama diperiksa,
 * bukan saat start, sehingga test ini tidak memerlukan jaringan. Dekoder yang
 * dibangun dari issuer-uri saja akan menarik metadata OIDC saat start dan
 * membuat test bergantung pada internet.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=dev",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.invalid/.well-known/jwks.json",
})
class CognitoProfileContextTest {

    @Autowired
    private EmployeeDirectory employeeDirectory;

    @Test
    @DisplayName("profil non-dummy memakai HrisEmployeeDirectory")
    void memakaiDirektoriHris() {
        assertThat(employeeDirectory).isInstanceOf(HrisEmployeeDirectory.class);
    }
}
