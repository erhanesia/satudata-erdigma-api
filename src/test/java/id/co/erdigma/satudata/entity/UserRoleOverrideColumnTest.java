package id.co.erdigma.satudata.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import id.co.erdigma.satudata.enums.HrisPermissionLevel;
import id.co.erdigma.satudata.enums.Role;
import id.co.erdigma.satudata.repository.UserRepository;

@SpringBootTest(properties = {
        "spring.profiles.active=dev",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.invalid/.well-known/jwks.json",
})
class UserRoleOverrideColumnTest {

    @Autowired
    private UserRepository userRepository;

    private String cognitoId;

    @AfterEach
    void bersihkan() {
        if (cognitoId != null) {
            userRepository.findByCognitoId(cognitoId)
                    .ifPresent(u -> userRepository.deleteAllInBatch(java.util.List.of(u)));
        }
    }

    @Test
    @DisplayName("ketiga kolom override tersimpan dan terbaca kembali")
    void kolomOverrideBolakBalik() {
        cognitoId = "it-test-" + UUID.randomUUID();
        UUID pengubah = UUID.randomUUID();
        // Postgres TIMESTAMP hanya menyimpan presisi mikrodetik; tanpa truncate
        // ini perbandingan pulang-pergi gagal karena nanodetik ikut terbawa.
        LocalDateTime saat = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);

        User baris = new User();
        baris.setCognitoId(cognitoId);
        baris.setEmail("override@erdigma.co.id");
        baris.setName("Uji Override");
        baris.setRole(Role.ADMIN);
        baris.setHrisPermissionLevel(HrisPermissionLevel.STAFF);
        baris.setRoleOverride(Role.ADMIN);
        baris.setRoleOverrideBy(pengubah);
        baris.setRoleOverrideAt(saat);
        userRepository.save(baris);

        Optional<User> hasil = userRepository.findByCognitoId(cognitoId);

        assertThat(hasil).isPresent();
        assertThat(hasil.get().getRoleOverride()).isEqualTo(Role.ADMIN);
        assertThat(hasil.get().getRoleOverrideBy()).isEqualTo(pengubah);
        assertThat(hasil.get().getRoleOverrideAt()).isEqualTo(saat);
    }
}
