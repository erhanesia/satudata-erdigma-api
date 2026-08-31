package id.co.erdigma.satudata.modules.dataset.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Isi permintaan untuk mengganti tag posisi sebuah dataset.
 *
 * Daftarnya MENGGANTI, bukan menambah. Operasi "tambah satu posisi" yang
 * berjalan sendiri-sendiri membuat dua admin yang menyunting bersamaan
 * menghasilkan gabungan yang tidak dikehendaki siapa pun; mengirim daftar utuh
 * membuat yang terakhir menyimpan tahu persis apa yang ia simpan.
 */
@Data
public class DatasetPositionUpdateDTO {

    @Schema(description = "Daftar posisi yang berlaku setelah perubahan. Kirim daftar kosong "
            + "untuk melepas seluruh tag.", example = "[\"Direksi\", \"General Manager\", \"Manager\"]")
    private List<String> positions;
}
