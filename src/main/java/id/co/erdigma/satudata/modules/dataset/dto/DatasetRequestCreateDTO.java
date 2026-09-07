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

    @Schema(description = "Aturan siapa yang boleh melihat dataset ini. "
            + "**Kosongkan agar terbuka untuk seluruh karyawan.** Diisi berarti hanya yang "
            + "cocok dengan **salah satu** aturan yang bisa membuka, membaca isi tabelnya, "
            + "dan mengunduhnya; yang lain mendapat 403. ADMIN dan pengunggahnya sendiri "
            + "selalu bisa. "
            + "Ketiga jenis aturan berdiri sejajar, bukan saling mempersempit: "
            + "`JOB_LEVEL` bersama `EMPLOYEE` berarti seluruh pemilik jenjang itu **dan** "
            + "karyawan yang ditunjuk, bukan irisan keduanya. Lihat `AccessRuleDTO` untuk "
            + "bentuk `ruleValue` tiap jenisnya.",
            example = "[{\"ruleType\":\"JOB_LEVEL\",\"ruleValue\":\"Senior Manager\"},"
                    + "{\"ruleType\":\"EMPLOYEE\",\"ruleValue\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"}]")
    private List<@jakarta.validation.Valid AccessRuleDTO> accessRules;

    @Schema(description = "Keterangan tiap berkas yang diunggah — nama versi manusia dan jenisnya. "
            + "Urutannya HARUS sama dengan urutan bagian multipart `files`, dan jumlahnya harus "
            + "sama persis. Boleh dikosongkan kalau hanya satu berkas: namanya diambil dari judul "
            + "dataset dan jenisnya dari ekstensi berkasnya.")
    private List<FileMeta> files;

    /**
     * Keterangan satu berkas.
     *
     * {@code format} tetap diminta walaupun bisa ditebak dari ekstensi, karena
     * desain memang menampilkannya sebagai pilihan. Server tetap memeriksanya
     * terhadap berkas yang sungguh dikirim — pilihan yang tidak cocok ditolak,
     * bukan diam-diam diperbaiki. Lencana "PDF" pada berkas yang isinya CSV
     * adalah keterangan yang salah, dan keterangan salah pada katalog data
     * lebih berbahaya daripada penolakan.
     */
    @Data
    public static class FileMeta {

        @Size(max = 255, message = "Nama file maksimal 255 karakter")
        @Schema(description = "Nama berkas versi manusia.", example = "Kamus Kolom")
        private String label;

        @Schema(description = "Jenis berkas: CSV, XLSX, PDF, atau DOCX. Harus cocok dengan "
                + "ekstensi berkas yang dikirim.", example = "CSV")
        private String format;
    }
}
