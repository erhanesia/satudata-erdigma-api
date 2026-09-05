package id.co.erdigma.satudata.modules.dataset.dto;

import id.co.erdigma.satudata.enums.AccessRuleType;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Satu aturan "siapa boleh melihat", dipakai baik saat mengirim maupun menerima.
 *
 * Sengaja TIDAK membawa nama posisi atau karyawan. Menerjemahkan UUID menjadi
 * nama berarti memanggil HRIS, dan pada daftar dataset berisi lima puluh baris
 * itu berarti lima puluh panggilan — atau satu panggilan besar yang tetap
 * memperlambat halaman yang isinya bukan tentang posisi.
 *
 * Panel admin sudah memuat daftar posisi dan karyawan untuk isian pemilihnya,
 * jadi penerjemahan nama dilakukan di sana, di tempat datanya memang sudah ada.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccessRuleDTO {

    @NotNull(message = "Jenis aturan wajib diisi")
    @Schema(description = "Jenis pembatasan", example = "JOB_LEVEL")
    private AccessRuleType ruleType;

    @NotBlank(message = "Nilai aturan wajib diisi")
    @Schema(description = """
            Isinya bergantung `ruleType`:

            - `JOB_LEVEL` — nama enum job level HRIS, mis. `SENIOR_MANAGER`.
              Ambil daftarnya dari `GET /api/v1/job-levels`.
            - `POSITION` — UUID posisi HRIS. Ambil dari `GET /api/v1/positions`.
            - `EMPLOYEE` — UUID karyawan HRIS. Ambil dari `GET /api/v1/employees`.
            """, example = "SENIOR_MANAGER")
    private String ruleValue;
}
