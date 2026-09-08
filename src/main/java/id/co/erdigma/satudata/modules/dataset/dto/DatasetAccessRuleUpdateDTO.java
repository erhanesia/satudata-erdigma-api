package id.co.erdigma.satudata.modules.dataset.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Isi permintaan untuk mengganti aturan "siapa boleh melihat" sebuah dataset.
 *
 * Daftarnya MENGGANTI, bukan menambah. Operasi "tambah satu aturan" yang
 * berjalan sendiri-sendiri membuat dua admin yang menyunting bersamaan
 * menghasilkan gabungan yang tidak dikehendaki siapa pun; mengirim daftar utuh
 * membuat yang terakhir menyimpan tahu persis apa yang ia simpan.
 */
@Data
public class DatasetAccessRuleUpdateDTO {

    /*
      Wajib ADA, tetapi boleh kosong. Dua hal yang berbeda, dan bedanya
      menentukan.

      Tanpa @NotNull, badan permintaan {} membuat ruas ini bernilai null,
      validator mengembalikan daftar kosong, dan seluruh pembatasan dataset
      terhapus -- lalu dijawab 200. Ruas yang lupa dikirim tidak boleh berakibat
      sama dengan permintaan yang sengaja membuka.

      Daftar kosong tetap sah dan tetap berarti membuka untuk semua. Itu memang
      cara menghapus pembatasan, hanya sekarang harus dinyatakan terang-terangan
      alih-alih terjadi karena kelalaian.

      Perhatikan @NotNull ini baru berguna sejak @Valid dipasang di controller.
      Sebelum itu ia tidak pernah dijalankan sama sekali.
    */
    @NotNull(message = "Daftar aturan akses wajib disertakan. Kirim daftar kosong "
            + "kalau memang ingin membukanya untuk seluruh karyawan.")
    @Schema(description = "Aturan yang berlaku setelah perubahan. Ruas ini WAJIB ADA. "
            + "Kirim daftar kosong untuk membuka dataset ini bagi seluruh karyawan; "
            + "menghilangkan ruasnya ditolak dengan 400.", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<@Valid AccessRuleDTO> accessRules;
}
