package id.co.erdigma.satudata.modules.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.HrisPermissionLevel;
import id.co.erdigma.satudata.enums.Role;
import id.co.erdigma.satudata.exception.BusinessValidationException;
import id.co.erdigma.satudata.modules.user.dto.UserAdminResponse;
import id.co.erdigma.satudata.repository.UserRepository;

@SpringBootTest(properties = {
        "spring.profiles.active=dev",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.invalid/.well-known/jwks.json",
})
class UserAdminServiceTest {

    @Autowired
    private UserAdminService userAdminService;

    @Autowired
    private UserRepository userRepository;

    private final List<String> cognitoIdBuatanTest = new ArrayList<>();

    @AfterEach
    void hapusBarisBuatanTest() {
        List<User> baris = cognitoIdBuatanTest.stream()
                .map(userRepository::findByCognitoId)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
        userRepository.deleteAllInBatch(baris);
        cognitoIdBuatanTest.clear();
    }

    @Test
    @DisplayName("pencarian mencocokkan sebagian nama tanpa peduli besar-kecil huruf")
    void pencarianCocokSebagianTanpaPeduliHuruf() {
        String cognitoId = simpan("Zulfikar Ramadhan", "zulfikar.unik@erdigma.co.id",
                Role.STAFF, HrisPermissionLevel.STAFF, null);

        var hasil = userAdminService.daftar("zULFIkar",
                PageRequest.of(0, 20, Sort.by("name").ascending()));

        assertThat(hasil.getContent()).extracting(UserAdminResponse::getEmail)
                .contains("zulfikar.unik@erdigma.co.id");
        assertThat(cognitoId).isNotNull();
    }

    @Test
    @DisplayName("baris hasil override ditandai roleOverride, yang ikut HRIS null")
    void penandaSumberPeranTerbawaKeRespons() {
        simpan("Alpha Tertunjuk", "alpha.unik@erdigma.co.id",
                Role.ADMIN, HrisPermissionLevel.STAFF, Role.ADMIN);
        simpan("Beta Ikut Hris", "beta.unik@erdigma.co.id",
                Role.STAFF, HrisPermissionLevel.STAFF, null);

        var hasil = userAdminService.daftar("unik@erdigma.co.id",
                PageRequest.of(0, 20, Sort.by("name").ascending()));

        var alpha = hasil.getContent().stream()
                .filter(u -> "alpha.unik@erdigma.co.id".equals(u.getEmail())).findFirst().orElseThrow();
        var beta = hasil.getContent().stream()
                .filter(u -> "beta.unik@erdigma.co.id".equals(u.getEmail())).findFirst().orElseThrow();

        assertThat(alpha.getRoleOverride()).isEqualTo(Role.ADMIN);
        assertThat(alpha.getRole()).isEqualTo(Role.ADMIN);
        assertThat(alpha.getHrisPermissionLevel()).isEqualTo(HrisPermissionLevel.STAFF);
        assertThat(beta.getRoleOverride()).isNull();
    }

    @Test
    @DisplayName("menunjuk peran menyimpan override beserta jejak siapa dan kapan")
    void menunjukPeranMenyimpanJejak() {
        String cognitoPemanggil = simpan("Admin Hris", "adminhris.unik@erdigma.co.id",
                Role.ADMIN, HrisPermissionLevel.ADMIN, null);
        String cognitoTarget = simpan("Target Staf", "target.unik@erdigma.co.id",
                Role.STAFF, HrisPermissionLevel.STAFF, null);

        User pemanggil = userRepository.findByCognitoId(cognitoPemanggil).orElseThrow();
        User target = userRepository.findByCognitoId(cognitoTarget).orElseThrow();

        var hasil = userAdminService.ubahPeran(pemanggil, target.getId(), Role.ADMIN);

        assertThat(hasil.getRole()).isEqualTo(Role.ADMIN);
        assertThat(hasil.getRoleOverride()).isEqualTo(Role.ADMIN);
        assertThat(hasil.getRoleOverrideAt()).isNotNull();

        User tersimpan = userRepository.findByCognitoId(cognitoTarget).orElseThrow();
        assertThat(tersimpan.getRoleOverrideBy()).isEqualTo(pemanggil.getId());
        // Tingkat izin HRIS tidak boleh ikut berubah.
        assertThat(tersimpan.getHrisPermissionLevel()).isEqualTo(HrisPermissionLevel.STAFF);
    }

    @Test
    @DisplayName("role null mengembalikan peran mengikuti HRIS dan mengosongkan jejak")
    void roleNullMengembalikanKeHris() {
        String cognitoPemanggil = simpan("Admin Hris", "adminhris2.unik@erdigma.co.id",
                Role.ADMIN, HrisPermissionLevel.ADMIN, null);
        String cognitoTarget = simpan("Manajer Ditunjuk", "manajer.unik@erdigma.co.id",
                Role.ADMIN, HrisPermissionLevel.MANAGER, Role.ADMIN);

        User pemanggil = userRepository.findByCognitoId(cognitoPemanggil).orElseThrow();
        User target = userRepository.findByCognitoId(cognitoTarget).orElseThrow();

        var hasil = userAdminService.ubahPeran(pemanggil, target.getId(), null);

        // MANAGER dipetakan ke PUBLISHER oleh HrisRoleMapper.
        assertThat(hasil.getRole()).isEqualTo(Role.PUBLISHER);
        assertThat(hasil.getRoleOverride()).isNull();
        assertThat(hasil.getRoleOverrideAt()).isNull();

        User tersimpan = userRepository.findByCognitoId(cognitoTarget).orElseThrow();
        assertThat(tersimpan.getRoleOverrideBy()).isNull();
    }

    @Test
    @DisplayName("mengubah peran sendiri ditolak")
    void ubahPeranSendiriDitolak() {
        String cognitoId = simpan("Admin Sendiri", "sendiri.unik@erdigma.co.id",
                Role.ADMIN, HrisPermissionLevel.ADMIN, null);
        User pemanggil = userRepository.findByCognitoId(cognitoId).orElseThrow();

        assertThatThrownBy(() -> userAdminService.ubahPeran(pemanggil, pemanggil.getId(), Role.STAFF))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("sendiri");
    }

    private String simpan(String nama, String email, Role role,
            HrisPermissionLevel level, Role override) {
        String cognitoId = "it-test-" + UUID.randomUUID();
        cognitoIdBuatanTest.add(cognitoId);

        User baris = new User();
        baris.setCognitoId(cognitoId);
        baris.setName(nama);
        baris.setEmail(email);
        baris.setRole(role);
        baris.setHrisPermissionLevel(level);
        baris.setRoleOverride(override);
        if (override != null) {
            baris.setRoleOverrideAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        }
        baris.setUpdatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        return userRepository.save(baris).getCognitoId();
    }
}
