package id.co.erdigma.satudata.modules.user.port.hris;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import id.co.erdigma.satudata.enums.HrisPermissionLevel;
import id.co.erdigma.satudata.enums.Role;

/**
 * Tabel kasus diambil dari DUMMY_IDENTITIES di
 * satudata-erdigma/src/features/auth/model/types.ts — sepuluh identitas yang
 * dipakai mode dummy. Kalau pemetaan di sini melenceng, mode dummy dan mode
 * Cognito diam-diam memberi peran berbeda untuk orang yang sama.
 */
class HrisRoleMapperTest {

    @ParameterizedTest(name = "{0} / {1} / {2} -> {3} / {4}")
    @DisplayName("kesepuluh identitas dummy dipetakan seperti di types.ts")
    @CsvSource({
            // hrisRole, jobLevel,          position,                     level,               role
            "ADMIN,      Admin,             Project Manager Data & IT,    ADMIN,               ADMIN",
            "STAFF,      Direktur Utama,    Direktur Utama,               DIRECTOR,            ADMIN",
            "STAFF,      General Manager,   General Manager Produk,       DIRECTOR,            ADMIN",
            "STAFF,      Staff,             Corporate Secretary,          CORPORATE_SECRETARY, PUBLISHER",
            "STAFF,      Manager,           Manager Teknologi Informasi,  MANAGER,             PUBLISHER",
            "STAFF,      Coordinator,       Koordinator Kampanye Digital, MANAGER,             PUBLISHER",
            "STAFF,      Specialist,        Data Specialist,              STAFF,               STAFF",
            "STAFF,      Staff,             Staff Penjualan,              STAFF,               STAFF",
            "STAFF,      Non Staff,         Administrasi Umum,            STAFF,               STAFF",
            "STAFF,      Staff,             Staff Operasional,            STAFF,               STAFF",
    })
    void memetakanIdentitasDummy(
            String hrisRole, String jobLevel, String position,
            HrisPermissionLevel levelDiharapkan, Role roleDiharapkan) {

        HrisPermissionLevel level = HrisRoleMapper.permissionLevel(hrisRole, jobLevel, position);

        assertThat(level).isEqualTo(levelDiharapkan);
        assertThat(HrisRoleMapper.role(level)).isEqualTo(roleDiharapkan);
    }

    @Test
    @DisplayName("Direktur tetap DIRECTOR walau bukan Direktur Utama")
    void direkturBiasa() {
        assertThat(HrisRoleMapper.permissionLevel("STAFF", "Direktur", "Direktur Keuangan"))
                .isEqualTo(HrisPermissionLevel.DIRECTOR);
    }

    @Test
    @DisplayName("Corporate Secretary berjenjang Manager BUKAN CORPORATE_SECRETARY")
    void corporateSecretaryHanyaDiJenjangStaf() {
        assertThat(HrisRoleMapper.permissionLevel("STAFF", "Manager", "Corporate Secretary"))
                .isEqualTo(HrisPermissionLevel.MANAGER);
    }

    @Test
    @DisplayName("jenjang tak dikenal jadi STAFF, bukan lemparan")
    void jenjangTakDikenal() {
        assertThat(HrisRoleMapper.permissionLevel("STAFF", "Kepala Suku", "Ketua Adat"))
                .isEqualTo(HrisPermissionLevel.STAFF);
    }

    @Test
    @DisplayName("jobLevel null tidak melempar NullPointerException")
    void jobLevelNull() {
        assertThat(HrisRoleMapper.permissionLevel("STAFF", null, null))
                .isEqualTo(HrisPermissionLevel.STAFF);
    }

    @Test
    @DisplayName("role ADMIN di HRIS menang atas jenjang apa pun")
    void adminMenang() {
        assertThat(HrisRoleMapper.permissionLevel("ADMIN", "Non Staff", "Magang"))
                .isEqualTo(HrisPermissionLevel.ADMIN);
    }
}
