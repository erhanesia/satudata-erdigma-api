package id.co.erdigma.satudata.modules.dataset.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Bagian metadata pada unggahan dataset. Berkasnya dikirim terpisah sebagai
 * bagian multipart bernama {@code file}.
 *
 * Yang TIDAK ada di sini disengaja: {@code rowCount}, {@code colCount},
 * {@code fileSize}, dan {@code format} dihitung dari berkasnya sendiri, dan
 * {@code divisionId} diambil dari akun pengunggah. Meminta manusia mengisi
 * angka yang bisa diverifikasi mesin hanya membuka peluang angka itu berbohong.
 */
@Data
public class DatasetRequestCreateDTO {

    @NotBlank(message = "Judul wajib diisi")
    @Size(max = 255, message = "Judul maksimal 255 karakter")
    @Schema(description = "Nama dataset seperti yang akan dibaca orang. Ini klaim tentang apa "
            + "data ini — tidak bisa ditebak dari berkasnya, jadi wajib ditulis penerbit.", example = "Penjualan Furnitur Ritel 2025", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Size(max = 60, message = "Slug maksimal 60 karakter")
    @Schema(description = "Alamat dataset di URL. **Kosongkan** dan sistem membuatnya dari judul. "
            + "Isi hanya bila ingin lebih pendek, misalnya `utilisasi-sdm` untuk judul "
            + "\"Utilisasi & Kapasitas Tim\". Huruf kecil, angka, dan tanda hubung saja. "
            + "Setelah terbit, slug tidak bisa diubah karena sudah jadi tautan di laporan orang.", example = "penjualan-furnitur-2025")
    private String slug;

    @Schema(description = "Penjelasan isi dataset dan cara membacanya.", example = "Transaksi penjualan furnitur ritel sepanjang 2025 per pesanan.")
    private String notes;

    @Schema(description = "Hal yang harus diketahui pembaca agar datanya tidak salah "
            + "ditafsirkan: angka yang masih sementara, batas cakupan, atau dasar perhitungan. "
            + "Ditampilkan sebagai blok terpisah di halaman detail.", example = "Status refund dan cancelled dihitung apa adanya tanpa rekonsiliasi keuangan.")
    private String disclaimer;

    @Schema(description = "Periode yang dicakup data.", example = "Jan – Des 2025")
    private String coverage;

    @Schema(description = "Nama topik, ambil dari GET /api/v1/topics. Boleh lebih dari satu.", example = "[\"Penjualan\"]")
    private List<String> topics;

    @Schema(description = "Slug koleksi induk, ambil dari GET /api/v1/collections. Boleh kosong.", example = "komersial")
    private String collectionSlug;
}
