package id.co.erdigma.satudata.modules.dataset.controller;

import java.util.List;
import java.util.Map;

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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;

import id.co.erdigma.satudata.annotation.CurrentUser;
import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.IdPrefix;
import id.co.erdigma.satudata.modules.dataset.dto.AccessRuleDTO;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetAccessRuleUpdateDTO;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetRequestCreateDTO;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetRequestGetDTO;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetResponse;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetResponseLite;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetSummaryResponse;
import id.co.erdigma.satudata.modules.dataset.dto.DatastoreResponse;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetResource;
import id.co.erdigma.satudata.modules.dataset.service.DatasetAdminService;
import id.co.erdigma.satudata.modules.dataset.service.DatasetReimportService;
import id.co.erdigma.satudata.modules.dataset.service.DatasetService;
import id.co.erdigma.satudata.modules.dataset.service.DatasetUploadService;
import id.co.erdigma.satudata.modules.dataset.service.DatastoreService;
import id.co.erdigma.satudata.modules.download.dto.DocumentTextResponse;
import id.co.erdigma.satudata.modules.download.dto.DownloadPayload;
import id.co.erdigma.satudata.modules.download.service.DownloadService;
import id.co.erdigma.satudata.modules.download.service.PreviewService;

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
        dan berkas sungguhan. Delapan lainnya adalah data contoh: metadata dan keterangan
        berkasnya ada — nama, jenis, ukuran — tetapi isi berkasnya sengaja tidak disertakan ke
        dalam repo. Karena itu `/datastore` mengembalikan kosong dan `/download` menjawab 400
        dengan keterangan bahwa berkasnya berupa contoh. Itu perilaku yang benar, bukan kerusakan.
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
    @Autowired
    private DatasetAdminService datasetAdminService;
    @Autowired
    private PreviewService previewService;
    @Autowired
    private DatasetReimportService datasetReimportService;

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
        return ResponseEntity.ok(datasetService.getAll(user, params));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','PUBLISHER')")
    @Operation(summary = "Terbitkan dataset baru dari berkas CSV", description = """
            **Versi internal untuk pengujian.** Desain sisi penerbit belum ada, jadi bentuk
            layarnya masih bisa berubah — tapi pembagian tugasnya sudah pasti: apa yang bisa
            diverifikasi mesin dihitung sendiri, apa yang butuh pertanggungjawaban manusia diminta
            dari penerbit.

            Permintaan berupa **multipart**:
            - `files` — satu atau beberapa berkas (CSV, XLSX, PDF, DOCX). Maksimal 10 MB per
              berkas, 40 MB seluruhnya, dan paling banyak 10 berkas.
            - `body` — metadata dalam JSON

            **Beberapa berkas dalam satu dataset.** Yang dibaca isinya menjadi tabel hanya
            **CSV pertama**; sisanya tersimpan sebagai berkas pendamping yang bisa diunduh — sama
            seperti XLSX dan PDF pada dataset contoh. Dataset tanpa CSV sama sekali tetap sah:
            berkasnya bisa diunduh, hanya `/datastore`-nya yang kosong.

            **Keterangan tiap berkas** dikirim lewat `body.files`, dan **dipasangkan menurut
            urutan** dengan bagian `files`. Jumlah keduanya harus sama persis. Boleh dikosongkan
            kalau tidak perlu memberi nama sendiri.

            **Yang dihitung sendiri, tidak perlu diisi:**

            | Field | Dari mana |
            |---|---|
            | `slug` | dibuat dari judul bila dikosongkan |
            | `division` | dari akun pengunggah — tidak bisa mengaku mewakili divisi lain |
            | `rowCount`, `colCount`, `fileSize` | dihitung dari berkasnya |
            | `format` | ekstensi tiap berkas |
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

            Perlu peran **ADMIN** atau **PUBLISHER** — di HRIS berarti jenjang direktur,
            manajerial, atau Corporate Secretary.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dataset terbit, berikut isi tabel dan berkasnya", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "Jenis berkas tidak didukung atau tidak cocok dengan ekstensinya, jumlah keterangan berkas tidak sama dengan jumlah berkas, judul kosong, slug bentrok, atau topik/koleksi/posisi tidak dikenal", content = @Content(schema = @Schema(type = "object", properties = @StringToClassMapItem(key = "error", value = String.class)))),
            @ApiResponse(responseCode = "403", description = "Peran Anda tidak berhak menerbitkan dataset", content = @Content(schema = @Schema(type = "object", properties = @StringToClassMapItem(key = "error", value = String.class))))
    })
    public ResponseEntity<DatasetResponse> create(@CurrentUser User user,
            @Parameter(description = "Satu atau beberapa berkas. Ulangi bagian `files` untuk tiap berkas.") @RequestPart("files") List<MultipartFile> files,
            @Valid @RequestPart("body") DatasetRequestCreateDTO body) {
        return ResponseEntity.ok(datasetUploadService.upload(user, body, files));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Ambil metadata lengkap satu dataset", description = """
            Semua keterangan tentang satu dataset: judul, divisi penerbit, catatan, disclaimer,
            koleksi induk, daftar topik dan format, jumlah baris dan kolom, serta **skema kolomnya**
            (nama mesin, nama tampilan, tipe data, satuan).

            Dipakai halaman detail dataset di portal untuk mengisi tab Ringkasan dan tab Kolom.

            Perhatikan `resources` — kalau kosong, dataset itu tidak punya berkas untuk diunduh.

            **Endpoint ini menaikkan penghitung kunjungan** setiap kali dipanggil. Untuk membaca
            tanpa ikut menghitung — misalnya dari panel pengelolaan — isi `recordView` dengan
            `false`.
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
                    """, example = "penjualan-furnitur-2025", required = true) @PathVariable String slug,
            @Parameter(description = """
                    Apakah pemanggilan ini dihitung sebagai kunjungan. Biarkan `true` untuk
                    halaman detail portal; isi `false` untuk pembacaan pengelolaan, supaya angka
                    "Total kunjungan" tidak naik setiap kali admin menengok datanya sendiri.
                    """, example = "true") @RequestParam(defaultValue = "true") boolean recordView) {
        return ResponseEntity.ok(datasetService.getBySlug(user, slug, recordView));
    }

    @PatchMapping("/{slug}/access-rules")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Ganti aturan siapa yang boleh melihat", description = """
            Mengganti SELURUH aturan akses sebuah dataset dengan daftar yang dikirim. Kirim daftar
            kosong untuk melepas semuanya.

            Tiap aturan punya `ruleType` dan `ruleValue`, dan ketiga jenisnya berdiri SEJAJAR —
            dataset terlihat bila salah satu aturan cocok:

            | `ruleType` | `ruleValue` | Ambil dari |
            | --- | --- | --- |
            | `JOB_LEVEL` | label jenjang, mis. `Senior Manager` | `GET /api/v1/job-levels` |
            | `POSITION` | UUID posisi HRIS | `GET /api/v1/positions` |
            | `EMPLOYEE` | UUID karyawan HRIS | `GET /api/v1/employees` |

            Aturan `EMPLOYEE` tidak lebih kuat daripada `JOB_LEVEL`, hanya lebih sempit. Dataset
            dengan `JOB_LEVEL=Manager` dan `EMPLOYEE=<Budi>` terlihat oleh seluruh Manager DAN oleh
            Budi — bukan oleh Manager yang kebetulan bernama Budi.

            Nilai yang tidak dikenal ditolak 400 supaya aturan hasil salah ketik tidak pernah
            tersimpan. Aturan seperti itu tidak akan pernah cocok dengan siapa pun, dan diam-diam
            mengunci datasetnya dari semua orang.

            **Ini mengubah hak akses, seketika.** Daftar kosong membuat dataset terbuka untuk
            seluruh karyawan. Yang tidak berhak tidak lagi melihatnya di `GET /api/v1/datasets`,
            dan mendapat 403 kalau membuka slug-nya langsung. ADMIN dan pengunggahnya sendiri
            selalu bisa.

            Perubahannya tercatat di `GET /api/v1/audit-logs` lengkap dengan nilai sebelum dan
            sesudahnya.

            Menggantikan `PATCH /{slug}/positions` yang menerima sembilan label karangan. Lihat
            changeset 47.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aturan akses tersimpan"),
            @ApiResponse(responseCode = "400", description = "Ada aturan yang tidak sah", content = @Content(schema = @Schema(type = "object", properties = @StringToClassMapItem(key = "error", value = String.class)))),
            @ApiResponse(responseCode = "403", description = "Bukan ADMIN", content = @Content),
            @ApiResponse(responseCode = "404", description = "Slug tidak dikenal", content = @Content)
    })
    public ResponseEntity<List<AccessRuleDTO>> updateAccessRules(@CurrentUser User user,
            @Parameter(description = "Slug dataset.", example = "penjualan-bulanan", required = true) @PathVariable String slug,
            @RequestBody DatasetAccessRuleUpdateDTO body) {
        return ResponseEntity.ok(datasetAdminService.updateAccessRules(user, slug, body.getAccessRules()));
    }

    @DeleteMapping("/{slug}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Hapus dataset dari katalog", description = """
            Penghapusan bersifat **soft delete**: barisnya tetap ada di database dengan penanda
            `deleted_at`, tapi hilang dari seluruh daftar dan tidak bisa dibuka lagi.

            Slug-nya TIDAK dilepas dan tidak bisa dipakai ulang. Itu disengaja — kalau slug bekas
            bisa diambil dataset lain, tautan lama di laporan orang akan diam-diam menunjuk ke data
            yang berbeda.

            Penghapusannya tercatat di `GET /api/v1/audit-logs`.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Dataset dihapus"),
            @ApiResponse(responseCode = "403", description = "Bukan ADMIN", content = @Content),
            @ApiResponse(responseCode = "404", description = "Slug tidak dikenal", content = @Content)
    })
    public ResponseEntity<Void> delete(@CurrentUser User user,
            @Parameter(description = "Slug dataset.", example = "penjualan-bulanan", required = true) @PathVariable String slug) {
        datasetAdminService.delete(user, slug);
        return ResponseEntity.noContent().build();
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

            **Satu dataset bisa punya lebih dari satu tabel.** Setiap berkas CSV atau Excel di
            dalamnya punya baris dan kolomnya sendiri. Pakai `resourceId` untuk memilih yang mana;
            tanpa itu, yang dijawab adalah berkas bertanda `tableSource` pada daftar `resources`.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Baris berhasil diambil. Bisa kosong kalau dataset belum berisi", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "Nilai page atau size tidak masuk akal, misalnya size 0", content = @Content(schema = @Schema(type = "object", properties = @StringToClassMapItem(key = "error", value = String.class)))),
            @ApiResponse(responseCode = "404", description = "Slug tidak dikenal", content = @Content(schema = @Schema(type = "object", properties = @StringToClassMapItem(key = "error", value = String.class))))
    })
    public ResponseEntity<DatastoreResponse> datastore(@CurrentUser User user,
            @Parameter(description = "Slug dataset. Hanya `penjualan-furnitur-2025` yang saat ini berisi data.", example = "penjualan-furnitur-2025", required = true) @PathVariable String slug,
            @Parameter(description = "Halaman ke berapa, **dimulai dari 0**.", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Jumlah baris per halaman. Minimal 1.", example = "50") @RequestParam(defaultValue = "50") int size,
            @Parameter(description = "Tabel milik berkas yang mana, dari daftar `resources`. Kosongkan untuk berkas utama.", example = "dres-f0000000-0000-4000-8000-000000000001") @RequestParam(required = false) String resourceId) {
        return ResponseEntity.ok(datastoreService.search(user, slug,
                resourceId == null || resourceId.isBlank() ? null : IdPrefix.DATASET_RESOURCE.parse(resourceId),
                page, size));
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
            @Parameter(description = "Nama kolom angka yang dijumlahkan. Kosongkan untuk sekadar menghitung banyaknya baris.", example = "total_sales") @RequestParam(required = false) String metric,
            @Parameter(description = "Tabel milik berkas yang mana, dari daftar `resources`. Kosongkan untuk berkas utama.", example = "dres-f0000000-0000-4000-8000-000000000001") @RequestParam(required = false) String resourceId) {
        return ResponseEntity.ok(datastoreService.summary(user, slug,
                resourceId == null || resourceId.isBlank() ? null : IdPrefix.DATASET_RESOURCE.parse(resourceId),
                groupBy, metric));
    }

    /**
     * Parameter {@code agreement} mewakili centang persetujuan pada modal di
     * desain. Tanpa itu unduhan ditolak, dan nilainya ikut tercatat di audit log.
     */
    @PostMapping("/{slug}/reimport")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Baca ulang isi tabel dari berkas yang sudah tersimpan", description = """
            Menghapus baris dan kolom dataset ini, lalu menulisnya ulang dari berkas **CSV atau
            Excel** miliknya yang sudah ada di penyimpanan. Berkasnya sendiri tidak diunggah ulang
            dan tidak berubah.

            **Kapan dipakai:**
            - Dataset diunggah sebelum portal bisa membaca Excel, sehingga tabelnya kosong.
            - Importir diperbaiki — penebakan tipe kolom, pemisah baru — dan dataset lama perlu
              ikut menikmati perbaikannya.

            Metadata tidak disentuh: judul, catatan, topik, tag posisi, pengunggah, dan penghitung
            unduhan tetap seperti semula. Pembacaan ulangnya tercatat di log audit.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Jumlah baris setelah dibaca ulang"),
            @ApiResponse(responseCode = "400", description = "Tidak ada berkas CSV/Excel yang bisa dibaca", content = @Content),
            @ApiResponse(responseCode = "403", description = "Bukan ADMIN", content = @Content),
            @ApiResponse(responseCode = "404", description = "Slug tidak dikenal", content = @Content)
    })
    public ResponseEntity<Map<String, Long>> reimport(@CurrentUser User user,
            @Parameter(description = "Slug dataset.", required = true) @PathVariable String slug) {
        return ResponseEntity.ok(Map.of("rowCount", datasetReimportService.reimport(user, slug)));
    }

    @GetMapping("/{slug}/preview")
    @Operation(summary = "Tampilkan berkas PDF di halaman", description = """
            Mengalirkan berkas **PDF** dengan `Content-Disposition: inline`, supaya peramban
            menggambarnya sendiri di dalam halaman alih-alih menyimpannya ke disk.

            **Pratinjau tetap tercatat** di log dengan `accessType = PREVIEW`. Ia tidak melewati
            modal persetujuan, tapi byte-nya tetap keluar dan isinya tetap terbaca utuh — berkas
            rahasia yang bisa dibaca tanpa jejak membuat seluruh guna log itu hilang.

            Untuk dokumen Word, pakai `/preview/text`. Untuk CSV dan XLSX tidak perlu: isinya
            sudah menjadi tabel dataset.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Berkas dialirkan untuk digambar peramban"),
            @ApiResponse(responseCode = "400", description = "Jenis berkas tidak bisa digambar peramban", content = @Content),
            @ApiResponse(responseCode = "403", description = "Dataset dibatasi untuk posisi lain", content = @Content),
            @ApiResponse(responseCode = "404", description = "Slug atau berkas tidak dikenal", content = @Content)
    })
    public ResponseEntity<InputStreamResource> preview(@CurrentUser User user,
            @Parameter(description = "Slug dataset.", example = "penjualan-bulanan", required = true) @PathVariable String slug,
            @Parameter(description = "Berkas mana yang dilihat, dari daftar `resources`.") @RequestParam(required = false) String resourceId,
            HttpServletRequest request) {

        DownloadPayload payload = previewService.preview(user, slug,
                resourceId == null || resourceId.isBlank() ? null : IdPrefix.DATASET_RESOURCE.parse(resourceId),
                resolveClientIp(request), request.getHeader("User-Agent"));
        DatasetResource resource = payload.getResource();

        return ResponseEntity.ok()
                // inline, bukan attachment: tujuannya digambar di halaman.
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + resource.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(resource.getSizeBytes())
                .body(new InputStreamResource(payload.getContent()));
    }

    @GetMapping("/{slug}/preview/text")
    @Operation(summary = "Baca isi dokumen Word sebagai teks", description = """
            Mengembalikan paragraf sebuah berkas **DOCX** dalam bentuk teks polos.

            **Yang tidak ikut:** tata letak, tabel, gambar, header, dan footer. Peramban tidak bisa
            menggambar .docx, dan mengurainya tanpa mesin render dokumen hanya menghasilkan
            teksnya. Antarmuka menyebutkan batas ini apa adanya — pembaca yang mengira sudah
            melihat dokumen lengkap padahal belum akan mengambil keputusan dari setengah isi.

            Sama seperti `/preview`, pembacaannya tercatat dengan `accessType = PREVIEW`.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Isi dokumen berhasil dibaca", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "Berkas bukan DOCX atau gagal dibaca", content = @Content),
            @ApiResponse(responseCode = "403", description = "Dataset dibatasi untuk posisi lain", content = @Content)
    })
    public ResponseEntity<DocumentTextResponse> previewText(@CurrentUser User user,
            @Parameter(description = "Slug dataset.", example = "penjualan-bulanan", required = true) @PathVariable String slug,
            @Parameter(description = "Berkas mana yang dibaca, dari daftar `resources`.") @RequestParam(required = false) String resourceId,
            HttpServletRequest request) {

        return ResponseEntity.ok(previewService.previewText(user, slug,
                resourceId == null || resourceId.isBlank() ? null : IdPrefix.DATASET_RESOURCE.parse(resourceId),
                resolveClientIp(request), request.getHeader("User-Agent")));
    }

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
            @Parameter(description = """
                    Berkas mana yang diminta, diisi `id` dari daftar `resources` pada
                    `GET /api/v1/datasets/{slug}`. Boleh berprefiks (`dres-…`) maupun UUID polos.

                    Dikosongkan berarti berkas pertama. Satu permintaan mengambil satu berkas;
                    untuk beberapa berkas, panggil endpoint ini sekali per berkas supaya
                    masing-masing punya barisnya sendiri di log unduhan.
                    """, example = "dres-f0000000-0000-4000-8000-000000000001") @RequestParam(required = false) String resourceId,
            HttpServletRequest request) {

        DownloadPayload payload = downloadService.download(user, slug,
                resourceId == null || resourceId.isBlank() ? null : IdPrefix.DATASET_RESOURCE.parse(resourceId),
                agreement, resolveClientIp(request), request.getHeader("User-Agent"));

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
