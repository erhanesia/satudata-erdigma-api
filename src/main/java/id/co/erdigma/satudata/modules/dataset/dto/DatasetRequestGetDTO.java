package id.co.erdigma.satudata.modules.dataset.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Parameter halaman Datasets. Nama nilai {@code sort} mengikuti desain:
 * relevance (bawaan), downloads, updated.
 *
 * Seluruh penyaring bersifat opsional — dikosongkan berarti tidak menyaring,
 * dan seluruh dataset tampil.
 */
@Data
public class DatasetRequestGetDTO {

    @Schema(description = "Kata kunci pada judul dan catatan dataset. "
            + "Kosongkan untuk menampilkan semua.", example = "penjualan")
    private String search;

    @Schema(description = "Saring per topik. Ulangi parameter untuk lebih dari satu, "
            + "misalnya topics=Keuangan&topics=Operasional. Daftar nilainya dari GET /api/v1/topic.")
    private List<String> topics;

    @Schema(description = "Saring per format berkas. Daftar nilainya dari GET /api/v1/format.")
    private List<String> formats;

    @Schema(description = "Saring per kode divisi: DNA, IT, PROD, SALES, FIN, OPS, HR, MKT. "
            + "Daftar lengkapnya dari GET /api/v1/division.")
    private List<String> divisions;

    @Schema(description = "Urutan hasil.", allowableValues = { "relevance", "downloads",
            "updated" }, defaultValue = "relevance")
    private String sort = "relevance";

    @Schema(description = "Halaman ke berapa, dimulai dari 0.", defaultValue = "0", minimum = "0")
    private int page = 0;

    @Schema(description = "Jumlah baris per halaman. Minimal 1.", defaultValue = "10", minimum = "1")
    private int size = 10;

    @Schema(description = "Ikut menampilkan dataset yang sudah dihapus.", defaultValue = "false")
    private boolean withDeleted = false;
}
