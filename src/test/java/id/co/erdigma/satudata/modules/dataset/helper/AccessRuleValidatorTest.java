package id.co.erdigma.satudata.modules.dataset.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import id.co.erdigma.satudata.enums.AccessRuleType;
import id.co.erdigma.satudata.exception.BusinessValidationException;
import id.co.erdigma.satudata.modules.dataset.dto.AccessRuleDTO;
import id.co.erdigma.satudata.modules.dataset.entity.AccessRule;

/**
 * Menjaga agar aturan yang cacat tidak berakhir sebagai dataset yang terbuka.
 *
 * <h2>Kenapa kelas ini ada</h2>
 *
 * Validator ini sempat MELEWATI aturan cacat diam-diam. Akibatnya berantai dan
 * tidak kelihatan dari kodenya sendiri: daftar berisi satu aturan cacat berakhir
 * sebagai daftar kosong, {@code DatasetAdminService} mengganti seluruh aturan
 * dataset dengan daftar kosong itu, dan daftar kosong berarti terbuka untuk
 * seluruh karyawan. Permintaan yang salah bentuk menghapus seluruh pembatasan
 * sebuah dataset, lalu menjawab 200.
 *
 * Yang membuatnya berbahaya bukan besarnya, melainkan arahnya. Seluruh keputusan
 * lain di sekitarnya gagal ke arah menutup: {@code DatasetAccessGuard} menolak
 * kalau ragu, {@code StoredFileCleaner} menyimpan kalau ragu. Yang ini justru
 * membuka.
 *
 * <h2>Batas yang harus tetap dijaga</h2>
 *
 * Daftar KOSONG tetap sah dan tetap berarti membuka. Itu memang cara menghapus
 * pembatasan, dan didokumentasikan begitu. Yang ditolak hanya daftar BERISI yang
 * salah bentuk, karena pengirimnya jelas bermaksud memasang aturan dan gagal.
 * Tanpa tes yang memisahkan keduanya, perbaikan berikutnya gampang menutup
 * jalur sah itu sekalian.
 */
class AccessRuleValidatorTest {

    private final AccessRuleValidator validator = new AccessRuleValidator();

    private AccessRuleDTO rule(AccessRuleType type, String value) {
        return new AccessRuleDTO(type, value);
    }

    @Test
    @DisplayName("aturan tanpa nilai ditolak, bukan dilewati diam-diam")
    void rejectsRuleWithoutValue() {
        // Inti temuannya. Sebelum diperbaiki, ini menghasilkan daftar kosong,
        // dan daftar kosong menghapus seluruh pembatasan datasetnya.
        assertThatThrownBy(() -> validator.validate(List.of(rule(AccessRuleType.POSITION, null))))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("tanpa nilai");

        assertThatThrownBy(() -> validator.validate(List.of(rule(AccessRuleType.JOB_LEVEL, "   "))))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("tanpa nilai");
    }

    @Test
    @DisplayName("aturan tanpa jenis ditolak")
    void rejectsRuleWithoutType() {
        assertThatThrownBy(() -> validator.validate(List.of(rule(null, "Senior Manager"))))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("tanpa jenis");
    }

    @Test
    @DisplayName("satu aturan cacat di antara yang sah tetap menggagalkan seluruhnya")
    void rejectsWholeListWhenOneEntryIsMalformed() {
        // Tidak menyimpan sebagian. Menyimpan yang sah saja berarti dataset
        // berakhir dengan pembatasan yang lebih longgar daripada yang diminta,
        // dan pengirimnya tetap menerima 200.
        List<AccessRuleDTO> requested = List.of(
                rule(AccessRuleType.JOB_LEVEL, "Senior Manager"),
                rule(AccessRuleType.POSITION, ""));

        assertThatThrownBy(() -> validator.validate(requested))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    @DisplayName("daftar kosong TETAP SAH dan berarti terbuka untuk semua")
    void emptyListStaysValid() {
        // Batas yang harus dijaga. Ini satu-satunya cara menghapus pembatasan,
        // dan perbaikan di atas tidak boleh ikut menutupnya.
        assertThat(validator.validate(List.of())).isEmpty();
        assertThat(validator.validate(null)).isEmpty();
    }

    @Test
    @DisplayName("null di dalam daftar ditolak")
    void rejectsNullEntry() {
        // Arrays.asList, bukan List.of: List.of menolak null lebih dulu, jadi
        // tesnya tidak akan pernah sampai ke validator.
        assertThatThrownBy(() -> validator.validate(Arrays.asList((AccessRuleDTO) null)))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    @DisplayName("JOB_LEVEL disimpan dalam bentuk label baku, apa pun huruf besar-kecilnya")
    void normalisesJobLevelLabel() {
        List<AccessRule> hasil = validator.validate(List.of(rule(AccessRuleType.JOB_LEVEL, "senior manager")));

        assertThat(hasil).hasSize(1);
        assertThat(hasil.get(0).getRuleValue()).isEqualTo("Senior Manager");
    }

    @Test
    @DisplayName("jenjang yang tidak dikenal ditolak")
    void rejectsUnknownJobLevel() {
        assertThatThrownBy(() -> validator.validate(List.of(rule(AccessRuleType.JOB_LEVEL, "Panglima"))))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("tidak dikenal");
    }

    @Test
    @DisplayName("POSITION dan EMPLOYEE wajib berupa UUID")
    void rejectsNonUuidForPositionAndEmployee() {
        assertThatThrownBy(() -> validator.validate(List.of(rule(AccessRuleType.POSITION, "Data Manager"))))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("UUID");

        assertThatThrownBy(() -> validator.validate(List.of(rule(AccessRuleType.EMPLOYEE, "budi"))))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("UUID");
    }

    @Test
    @DisplayName("duplikat tetap dibuang diam-diam, bukan ditolak")
    void stillDropsDuplicatesSilently() {
        // Berbeda dari aturan cacat, dan bedanya disengaja. Mengirim posisi yang
        // sama dua kali adalah kecerobohan antarmuka, bukan kesalahan penerbit,
        // dan membuangnya tidak melonggarkan pembatasan apa pun.
        UUID id = UUID.randomUUID();
        List<AccessRule> hasil = validator.validate(List.of(
                rule(AccessRuleType.POSITION, id.toString()),
                rule(AccessRuleType.POSITION, id.toString())));

        assertThat(hasil).hasSize(1);
    }

    @Test
    @DisplayName("ketiga jenis aturan yang sah lolos bersama")
    void acceptsAllThreeAxes() {
        assertThatCode(() -> validator.validate(List.of(
                rule(AccessRuleType.JOB_LEVEL, "Manager"),
                rule(AccessRuleType.POSITION, UUID.randomUUID().toString()),
                rule(AccessRuleType.EMPLOYEE, UUID.randomUUID().toString()))))
                .doesNotThrowAnyException();
    }
}
