package id.co.erdigma.satudata.modules.admin.controller;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import id.co.erdigma.satudata.annotation.CurrentUser;
import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetRequestGetDTO;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetResponseLite;
import id.co.erdigma.satudata.modules.dataset.service.DatasetService;
import id.co.erdigma.satudata.modules.stats.dto.DailyDownloadResponse;
import id.co.erdigma.satudata.modules.stats.dto.StatsResponse;
import id.co.erdigma.satudata.modules.stats.service.StatsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Pintu panel admin untuk data yang juga dilihat portal.
 *
 * <h2>Kenapa ada jalur tersendiri</h2>
 *
 * Dua endpoint di sini menyajikan data yang sama dengan endpoint portal, tetapi
 * dengan aturan yang berbeda: <b>panel admin dibatasi divisi si admin,
 * sedangkan portal tidak</b>. Seorang admin yang menelusuri katalog portal harus
 * melihat apa yang dilihat karyawan lain; yang dibatasi hanya apa yang boleh ia
 * kelola.
 *
 * Endpoint log akses dan jejak audit TIDAK ada di sini, dan itu disengaja:
 * keduanya memang sudah hanya untuk admin, jadi pembatasannya dipasang di
 * tempatnya masing-masing tanpa perlu jalur kembar.
 *
 * <h2>Kenapa bukan parameter pada endpoint yang lama</h2>
 *
 * Parameter yang mengubah arti otorisasi itu halus. Pemanggil yang lupa
 * mengirimnya tidak mendapat galat, melainkan diam-diam melihat lebih banyak
 * dari yang seharusnya. Jalur yang berbeda tidak bisa lupa dikirim.
 *
 * <h2>Kenapa bukan {@code /api/v1/datasets/admin}</h2>
 *
 * Karena bertabrakan dengan {@code GET /api/v1/datasets/{slug}} yang sudah ada.
 * Spring akan memenangkan jalur harfiahnya, dan dataset yang kebetulan
 * ber-slug {@code admin} menjadi tidak bisa dibuka selamanya. Jarang terjadi,
 * dan justru karena jarang, sangat sulit dilacak saat terjadi.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "10. Panel admin", description = """
        Data panel admin, dibatasi divisi si admin.

        **Admin hanya berurusan dengan dataset divisinya sendiri.** Divisi di sini adalah
        *team* di HRIS, dan sebuah dataset mewarisi divisi orang yang mengunggahnya.

        Kecualinya **admin HRIS**, yaitu karyawan yang di HRIS tercatat ber-`hrisRole = ADMIN`.
        Ia berurusan dengan seluruh divisi. Perhatikan bahwa ini BUKAN peran portal: peran
        portal bisa ditunjuk dari panel pengguna, sedangkan tingkat izin HRIS hanya datang
        dari HRIS. Kalau yang dipakai peran portal, seorang admin bisa menunjuk admin baru
        yang seketika melihat seluruh divisi.

        Endpoint di sini **melengkapi**, bukan menggantikan, `GET /api/v1/datasets` dan
        `GET /api/v1/stats`. Keduanya tetap melayani portal tanpa pembatasan divisi.
        """)
public class AdminPanelController {

    @Autowired
    private DatasetService datasetService;
    @Autowired
    private StatsService statsService;

    @GetMapping("/datasets")
    @Operation(summary = "Daftar dataset panel admin, dibatasi divisi", description = """
            Sama dengan `GET /api/v1/datasets`, dengan satu batas tambahan: hanya dataset
            divisi si admin yang dikembalikan.

            Batas itu **tidak bisa dilepas lewat parameter apa pun**. Mengisi `divisions`
            dengan divisi lain tidak menghasilkan dataset divisi itu melainkan halaman
            kosong, karena kedua syarat harus terpenuhi sekaligus.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Daftar dataset", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "403", description = "Bukan ADMIN, atau akunnya belum terhubung ke divisi mana pun di HRIS", content = @Content)
    })
    public ResponseEntity<Page<DatasetResponseLite>> datasets(@CurrentUser User user,
            @ParameterObject DatasetRequestGetDTO params) {
        return ResponseEntity.ok(datasetService.getAllForAdmin(user, params));
    }

    @GetMapping("/stats")
    @Operation(summary = "Angka dasbor panel admin, dibatasi divisi", description = """
            Sama dengan `GET /api/v1/stats`, tetapi angkanya hanya mencakup divisi si admin.

            **Yang tidak ikut dibatasi:** jumlah topik, format, dan divisi. Ketiganya data
            acuan bersama, bukan cerminan cakupan si admin; banyaknya team di Erdigma tetap
            sama siapa pun yang bertanya.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Angka dasbor", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "403", description = "Bukan ADMIN, atau akunnya belum terhubung ke divisi mana pun di HRIS", content = @Content)
    })
    public ResponseEntity<StatsResponse> stats(@CurrentUser User user) {
        return ResponseEntity.ok(statsService.getStatsForAdmin(user));
    }

    @GetMapping("/stats/downloads/daily")
    @Operation(summary = "Grafik unduhan harian panel admin, dibatasi divisi", description = """
            Sama dengan `GET /api/v1/stats/downloads/daily`, dibatasi divisi si admin.

            Hari yang tidak punya unduhan tetap muncul bernilai nol, jadi grafiknya tidak
            berlubang.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unduhan per hari", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "403", description = "Bukan ADMIN, atau akunnya belum terhubung ke divisi mana pun di HRIS", content = @Content)
    })
    public ResponseEntity<DailyDownloadResponse> dailyDownloads(@CurrentUser User user,
            @Parameter(description = "Berapa hari ke belakang, maksimum 365", example = "30") @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(statsService.getDailyDownloadsForAdmin(user, days));
    }
}
