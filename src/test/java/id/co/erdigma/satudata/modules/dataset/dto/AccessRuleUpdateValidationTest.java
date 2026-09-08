package id.co.erdigma.satudata.modules.dataset.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import id.co.erdigma.satudata.enums.AccessRuleType;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Menjaga batasan Bean Validation pada badan permintaan ubah aturan akses.
 *
 * <h2>Kenapa ini diuji terpisah dari AccessRuleValidator</h2>
 *
 * Keduanya menjaga hal yang berbeda dan pada lapisan yang berbeda.
 * {@code AccessRuleValidator} menilai ISI aturan setelah badan permintaannya
 * diterima. Batasan di sini menilai BENTUK badan permintaannya, sebelum satu
 * baris pun kode layanan berjalan.
 *
 * Yang paling menentukan: ruas {@code accessRules} yang HILANG tidak boleh
 * berakibat sama dengan daftar KOSONG. Tanpa {@code @NotNull}, badan permintaan
 * <code>{}</code> membuat ruasnya null, validator mengembalikan daftar kosong,
 * dan seluruh pembatasan dataset terhapus lalu dijawab 200. Ruas yang lupa
 * dikirim tidak boleh sediam itu akibatnya.
 *
 * <h2>Batasan ini bergantung pada @Valid di controller</h2>
 *
 * {@code @NotNull} di DTO tidak berarti apa-apa sampai {@code @RequestBody}-nya
 * ditandai {@code @Valid}. Keduanya harus ada, dan tes ini hanya membuktikan
 * separuhnya. Separuh lainnya dijaga keberadaan anotasi itu di
 * {@code DatasetController}.
 */
class AccessRuleUpdateValidationTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final Validator validator = FACTORY.getValidator();

    private DatasetAccessRuleUpdateDTO bodyWith(List<AccessRuleDTO> rules) {
        DatasetAccessRuleUpdateDTO body = new DatasetAccessRuleUpdateDTO();
        body.setAccessRules(rules);
        return body;
    }

    @Test
    @DisplayName("badan permintaan tanpa ruas accessRules ditolak")
    void rejectsMissingField() {
        // Inti temuannya. Badan permintaan {} menghasilkan keadaan ini, dan
        // sebelum diperbaiki ia menghapus seluruh pembatasan dataset.
        Set<ConstraintViolation<DatasetAccessRuleUpdateDTO>> violations =
                validator.validate(bodyWith(null));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("wajib disertakan");
    }

    @Test
    @DisplayName("daftar KOSONG tetap diterima, karena itu cara sah membuka untuk semua")
    void acceptsEmptyList() {
        // Batas yang harus dijaga. Perbaikan di atas tidak boleh ikut menutup
        // satu-satunya cara menghapus pembatasan.
        assertThat(validator.validate(bodyWith(List.of()))).isEmpty();
    }

    @Test
    @DisplayName("aturan tanpa nilai ditolak lewat @NotBlank pada elemennya")
    void rejectsBlankRuleValue() {
        // Membuktikan @Valid pada elemen daftar benar-benar merambat. Tanpa itu,
        // batasan di AccessRuleDTO tidak pernah dijalankan.
        Set<ConstraintViolation<DatasetAccessRuleUpdateDTO>> violations = validator.validate(
                bodyWith(List.of(new AccessRuleDTO(AccessRuleType.POSITION, "   "))));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("Nilai aturan wajib diisi");
    }

    @Test
    @DisplayName("aturan tanpa jenis ditolak lewat @NotNull pada elemennya")
    void rejectsNullRuleType() {
        Set<ConstraintViolation<DatasetAccessRuleUpdateDTO>> violations = validator.validate(
                bodyWith(List.of(new AccessRuleDTO(null, "Senior Manager"))));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("Jenis aturan wajib diisi");
    }

    @Test
    @DisplayName("aturan yang lengkap lolos tanpa pelanggaran")
    void acceptsWellFormedRules() {
        assertThat(validator.validate(bodyWith(List.of(
                new AccessRuleDTO(AccessRuleType.JOB_LEVEL, "Senior Manager"),
                new AccessRuleDTO(AccessRuleType.EMPLOYEE, UUID.randomUUID().toString())))))
                .isEmpty();
    }

    @Test
    @DisplayName("jalur UNGGAH tetap boleh tanpa aturan akses sama sekali")
    void uploadStillAllowsAbsentRules() {
        /*
         * Batas yang paling mudah dilanggar saat memperbaiki temuan ini.
         *
         * `null` berarti hal yang berbeda di dua jalur. Pada unggah ia berarti
         * "dataset baru ini tidak dibatasi", dan itu keadaan yang paling sering
         * terjadi. Pada ubah ia berarti "ruasnya lupa dikirim", dan akibatnya
         * menghapus pembatasan yang SUDAH ADA.
         *
         * Bedanya karena unggah menetapkan keadaan awal, sedangkan ubah
         * mengganti keadaan yang sudah berjalan. Karena itu @NotNull dipasang di
         * DTO ubah saja, bukan di AccessRuleValidator yang dipakai keduanya.
         * Kalau dipasang di validator, setiap unggahan tanpa pembatasan akan
         * gagal dengan 400.
         */
        DatasetRequestCreateDTO upload = new DatasetRequestCreateDTO();
        upload.setTitle("Laporan Penjualan");

        assertThat(upload.getAccessRules()).isNull();
        assertThat(validator.validateProperty(upload, "accessRules")).isEmpty();
    }
}
