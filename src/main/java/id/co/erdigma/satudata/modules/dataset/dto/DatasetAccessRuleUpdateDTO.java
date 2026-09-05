package id.co.erdigma.satudata.modules.dataset.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
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

    @Schema(description = "Aturan yang berlaku setelah perubahan. Kirim daftar kosong untuk "
            + "membuka dataset ini bagi seluruh karyawan.")
    private List<@Valid AccessRuleDTO> accessRules;
}
