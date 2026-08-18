package id.co.erdigma.satudata.modules.dataset.controller;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;

import id.co.erdigma.satudata.annotation.CurrentUser;
import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetRequestCreateDTO;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetRequestGetDTO;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetResponse;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetResponseLite;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetSummaryResponse;
import id.co.erdigma.satudata.modules.dataset.dto.DatastoreResponse;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetResource;
import id.co.erdigma.satudata.modules.dataset.service.DatasetService;
import id.co.erdigma.satudata.modules.dataset.service.DatasetUploadService;
import id.co.erdigma.satudata.modules.dataset.service.DatastoreService;
import id.co.erdigma.satudata.modules.download.dto.DownloadPayload;
import id.co.erdigma.satudata.modules.download.service.DownloadService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.StringToClassMapItem;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/datasets")
@Tag(name = "1. Datasets", description = """
        Katalog dataset — inti dari portal ini.

        **Alur khas pemakaian:**
        1. `GET /api/v1/datasets` untuk mencari dan menyaring, lalu ambil nilai `slug` dari hasilnya
        2. `GET /api/v1/datasets/{slug}` untuk metadata lengkap dan daftar kolom
        3. `GET /api/v1/datasets/{slug}/datastore` untuk melihat isi tabelnya
        4. `GET /api/v1/datasets/{slug}/summary` untuk angka agregat (bahan grafik)
        5. `GET /api/v1/datasets/{slug}/download` untuk mengunduh berkas aslinya

        **Penting:** dari 9 dataset yang ada, hanya `penjualan-furnitur-2025` yang punya isi tabel
        dan berkas. Delapan lainnya baru berisi metadata, sehingga `/datastore` akan kosong dan
        `/download` menjawab 404. Itu perilaku yang benar, bukan kerusakan.
        """)
public class DatasetController {
    @Autowired
    private DatasetService datasetService;
    @Autowired
    private DatastoreService datastoreService;
    @Autowired
    private DownloadService downloadService;
    @Autowired
    private DatasetUploadService datasetUploadService;

    @GetMapping()
    @Operation(summary = "Cari dan saring daftar dataset", description = """
            Mengembalikan daftar dataset berhalaman, sesuai isi halaman Datasets di portal.

            **Cara tercepat menampilkan semua:** tekan Execute tanpa mengisi apa pun.
            Seluruh penyaring bersifat opsional — yang dikosongkan tidak dikirim.

            **Contoh pemakaian:**
            - Cari kata kunci → isi `search` dengan `penjualan`
            - Saring dua topik → isi `topics` dua baris: `Keuangan` lalu `Operasional`
            - Saring dua divisi → isi `divisions` dua baris: `SALES` lalu `FIN`
            - Urutkan terpopuler → isi `sort` dengan `downloads`

            **Membaca hasilnya:** `content` berisi datasetnya, `totalElements` jumlah seluruhnya,
            `totalPages` jumlah halaman, dan `number` halaman saat ini — **dimulai dari 0**, bukan 1.

            Ambil nilai `slug` dari hasil di sini untuk dipakai di endpoint lain.
            """)
    @ApiResponse(responseCode = "200", description = "Daftar dataset berhasil diambil", useReturnTypeSchema = true)
    public ResponseEntity<Page<DatasetResponseLite>> index(@CurrentUser User user,
            @ParameterObject DatasetRequestGetDTO params) {
        return ResponseEntity.ok(datasetService.getAll(params));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','PUBLISHER')")
    @Operation(summary = "Terbitkan dataset baru dari berkas CSV", description = """
            **Versi internal untuk pengujian.** Desain sisi penerbit belum ada, jadi bentuk
            layarnya masih bisa berubah — tapi pembagian tugasnya sudah pasti: apa yang bisa
            diverifikasi mesin dihitung sendiri, apa yang butuh pertanggungjawaban manusia diminta
            dari penerbit.

            Permintaan berupa **multipart** dengan dua bagian:
            - `file` — berkas CSV, maksimal 10 MB
            - `body` — metadata dalam JSON

            **Yang dihitung sendiri, tidak perlu diisi:**

            | Field | Dari mana |
            |---|---|
            | `slug` | dibuat dari judul bila dikosongkan |
            | `division` | dari akun pengunggah — tidak bisa mengaku mewakili divisi lain |
            | `rowCount`, `colCount`, `fileSize` | dihitung dari berkasnya |
            | `format` | CSV |
            | daftar kolom, tipe data | dibaca dari header dan **isi** berkas |

            **Soal tipe kolom:** untuk kolom di luar 17 kolom CSV penjualan furnitur, tipe data
            ditebak dari mencicipi 200 nilai pertama. Tebakannya konservatif — ragu berarti `Text`.
            Salah menebak kolom angka sebagai teks hanya membuat grafik tidak menawarkan kolom itu;
            sebaliknya akan membuat perhitungan diam-diam mengabaikan baris yang tidak terbaca dan
            menghasilkan angka yang salah tanpa peringatan.

            **Soal slug:** kosongkan agar dibuatkan dari judul. Bila Anda mengisinya sendiri dan
            slug itu sudah dipakai, permintaan ditolak 400 beserta usulan yang tersedia — **tidak**
            diam-diam ditambahi angka, karena tautan yang sudah terlanjur dibagikan akan menunjuk
            ke tempat yang salah.

            Perlu peran **ADMIN** atau **PUBLISHER**. Identitas uji yang bisa dipakai:
            `dummy-admin`, `dummy-director`, `dummy-corpsec`, `dummy-manager`.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dataset terbit, berikut isi tabel dan berkasnya", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "Berkas bukan CSV, judul kosong, slug bentrok, atau topik/koleksi tidak dikenal", content = @Content(schema = @Schema(type = "object", properties = @StringToClassMapItem(key = "error", value = String.class)))),
            @ApiResponse(responseCode = "403", description = "Peran Anda tidak berhak menerbitkan dataset", content = @Content(schema = @Schema(type = "object", properties = @StringToClassMapItem(key = "error", value = String.class))))
    })
    public ResponseEntity<DatasetResponse> create(@CurrentUser User user,
            @Parameter(description = "Berkas CSV, maksimal 10 MB.") @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("body") DatasetRequestCreateDTO body) {
        return ResponseEntity.ok(datasetUploadService.upload(user, body, file));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Ambil metadata lengkap satu dataset", description = """
            Semua keterangan tentang satu dataset: judul, divisi penerbit, catatan, disclaimer,
            koleksi induk, daftar topik dan format, jumlah baris dan kolom, serta **skema kolomnya**
            (nama mesin, nama tampilan, tipe data, satuan).

            Dipakai halaman detail dataset di portal untuk mengisi tab Ringkasan dan tab Kolom.

            Perhatikan `resources` — kalau kosong, dataset itu tidak punya berkas untuk diunduh.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dataset ditemukan", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "Slug tidak dikenal", content = @Content(schema = @Schema(type = "object", properties = @StringToClassMapItem(key = "error", value = String.class))))
    })
    public ResponseEntity<DatasetResponse> getById(@CurrentUser User user,
            @Parameter(description = """
                    Identitas dataset di URL — berupa **slug**, bukan UUID.

                    Slug adalah versi judul yang aman dipakai di URL: huruf kecil, spasi diganti
                    tanda hubung. Contoh: judul "Penjualan Furnitur Ritel 2025" berslug
                    `penjualan-furnitur-2025`.

                    Daftar slug yang tersedia bisa dilihat dari `GET /api/v1/datasets`.
                    """, example = "penjualan-furnitur-2025", required = true) @PathVariable String slug) {
        return ResponseEntity.ok(datasetService.getBySlug(slug));
    }

    @GetMapping("/{slug}/datastore")
    @Operation(summary = "Baca isi tabel dataset per halaman", description = """
            Isi baris data yang sesungguhnya — yang tampil di tab **Data Explorer** portal.

            `columns` memberi urutan kolomnya, `rows` berisi datanya sebagai objek bebas
            (satu objek per baris, kuncinya nama kolom).

            **Contoh:** slug `penjualan-furnitur-2025`, `page` 0, `size` 50 → 50 baris pertama
            dari 10.000 baris penjualan furnitur.

            Naikkan `page` untuk melihat baris berikutnya. Dengan `size` 50, halaman 0 berisi baris
            1–50, halaman 1 berisi 51–100, dan seterusnya.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Baris berhasil diambil. Bisa kosong kalau dataset belum berisi", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "Nilai page atau size tidak masuk akal, misalnya size 0", content = @Content(schema = @Schema(type = "object", properties = @StringToClassMapItem(key = "error", value = String.class)))),
            @ApiResponse(responseCode = "404", description = "Slug tidak dikenal", content = @Content(schema = @Schema(type = "object", properties = @StringToClassMapItem(key = "error", value = String.class))))
    })
    public ResponseEntity<DatastoreResponse> datastore(@CurrentUser User user,
            @Parameter(description = "Slug dataset. Hanya `penjualan-furnitur-2025` yang saat ini berisi data.", example = "penjualan-furnitur-2025", required = true) @PathVariable String slug,
            @Parameter(description = "Halaman ke berapa, **dimulai dari 0**.", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Jumlah baris per halaman. Minimal 1.", example = "50") @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(datastoreService.search(slug, page, size));
    }

    @GetMapping("/{slug}/summary")
    @Operation(summary = "Hitung agregat untuk bahan grafik", description = """
            Mengelompokkan isi dataset lalu menghitung jumlahnya — inilah yang menggambar grafik
            di halaman detail portal.

            **Cara mengisi:**
            - `groupBy` **wajib** — nama kolom yang jadi pengelompokan, nantinya sumbu X grafik
            - `metric` opsional — nama kolom angka yang dijumlahkan. Kalau dikosongkan, yang keluar
              hanya `count` (banyaknya baris per kelompok)

            **Contoh untuk `penjualan-furnitur-2025`:**
            | Ingin melihat | `groupBy` | `metric` |
            |---|---|---|
            | Penjualan per kategori produk | `category` | `total_sales` |
            | Penjualan per kota | `kota` | `total_sales` |
            | Banyaknya transaksi per status | `status` | *(kosongkan)* |
            | Jumlah barang terjual per kategori | `category` | `quantity` |

            Nama kolom yang boleh dipakai bisa dilihat dari `columns` pada
            `GET /api/v1/datasets/{slug}`.

            **Peringatan angka:** kolom `status` memuat `completed`, `cancelled`, dan `refund`.
            Menjumlahkan `total_sales` tanpa menyaring status berarti transaksi batal ikut terhitung
            — jadi angkanya bukan pendapatan bersih. Penyaringan per status belum tersedia di
            endpoint ini.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agregat berhasil dihitung", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "Kolom groupBy atau metric tidak ada di dataset ini", content = @Content(schema = @Schema(type = "object", properties = @StringToClassMapItem(key = "error", value = String.class)))),
            @ApiResponse(responseCode = "404", description = "Slug tidak dikenal", content = @Content(schema = @Schema(type = "object", properties = @StringToClassMapItem(key = "error", value = String.class))))
    })
    public ResponseEntity<DatasetSummaryResponse> summary(@CurrentUser User user,
            @Parameter(description = "Slug dataset.", example = "penjualan-furnitur-2025", required = true) @PathVariable String slug,
            @Parameter(description = "Nama kolom pengelompokan — jadi sumbu X grafik. Contoh lain: `kota`, `status`.", example = "category", required = true) @RequestParam String groupBy,
            @Parameter(description = "Nama kolom angka yang dijumlahkan. Kosongkan untuk sekadar menghitung banyaknya baris.", example = "total_sales") @RequestParam(required = false) String metric) {
        return ResponseEntity.ok(datastoreService.summary(slug, groupBy, metric));
    }

    /**
     * Parameter {@code agreement} mewakili centang persetujuan pada modal di
     * desain. Tanpa itu unduhan ditolak, dan nilainya ikut tercatat di audit log.
     */
    @GetMapping("/{slug}/download")
    @Operation(summary = "Unduh berkas asli dataset", description = """
            Mengalirkan berkas sumber dataset apa adanya, tanpa perubahan sebyte pun.

            **`agreement` wajib diisi `true`.** Itu bukan formalitas: nilainya mewakili centang
            persetujuan pemakaian data pada modal di portal, dan **ikut tercatat di audit log**
            bersama identitas pengunduh, waktu, alamat IP, dan User-Agent. Dikirim `false` →
            ditolak 400. Jangan pernah mengirim `true` secara otomatis tanpa pengguna benar-benar
            menyetujuinya.

            **Catatan saat mencoba lewat Swagger:** responsnya berkas 1,4 MB, dan Swagger UI akan
            mencoba menampilkannya sebagai teks sebelum memberi tombol "Download file" — browser
            bisa terasa berat beberapa detik. Untuk sekadar menguji, lebih nyaman memakai `curl`.
            Yang justru berguna dicoba di sini adalah kasus gagalnya: biarkan `agreement` bernilai
            `false` lalu Execute, harus dijawab 400.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Berkas dialirkan. Nama berkasnya ada di header Content-Disposition"),
            @ApiResponse(responseCode = "400", description = "Persetujuan belum diberikan — agreement masih false", content = @Content(schema = @Schema(type = "object", properties = @StringToClassMapItem(key = "error", value = String.class)))),
            @ApiResponse(responseCode = "404", description = "Slug tidak dikenal, atau dataset itu memang tidak punya berkas", content = @Content(schema = @Schema(type = "object", properties = @StringToClassMapItem(key = "error", value = String.class))))
    })
    public ResponseEntity<InputStreamResource> download(@CurrentUser User user,
            @Parameter(description = "Slug dataset. Hanya `penjualan-furnitur-2025` yang punya berkas.", example = "penjualan-furnitur-2025", required = true) @PathVariable String slug,
            @Parameter(description = "Persetujuan pemakaian data. **Harus `true`**, dan ikut tercatat di audit log.", example = "true") @RequestParam(defaultValue = "false") boolean agreement,
            HttpServletRequest request) {

        DownloadPayload payload = downloadService.download(user, slug, agreement,
                resolveClientIp(request), request.getHeader("User-Agent"));

        DatasetResource resource = payload.getResource();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + resource.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(
                        resource.getContentType() != null ? resource.getContentType()
                                : MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .contentLength(resource.getSizeBytes())
                .body(new InputStreamResource(payload.getContent()));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
