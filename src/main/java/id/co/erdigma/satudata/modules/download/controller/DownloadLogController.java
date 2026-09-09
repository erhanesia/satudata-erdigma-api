package id.co.erdigma.satudata.modules.download.controller;

import java.time.LocalDate;
import id.co.erdigma.satudata.annotation.CurrentUser;
import id.co.erdigma.satudata.entity.User;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import id.co.erdigma.satudata.modules.download.dto.DownloadLogResponse;
import id.co.erdigma.satudata.modules.download.service.DownloadLogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/download-logs")
@Tag(name = "9. Log unduhan", description = """
        Siapa mengunduh berkas apa, kapan, dari alamat IP mana, dan apakah persetujuan
        pemakaian dicentang.

        Mengisi tab **Log unduhan** di halaman Log panel admin.

        **Hanya ADMIN.** Modal persetujuan di portal memuat klausul sanksi kebocoran data —
        klausul itu hanya bisa ditegakkan kalau catatan ini ada dan terbatas pembacanya.

        Baris di sini ditulis oleh `GET /api/v1/datasets/{slug}/download` **sebelum** byte
        pertama dikirim, jadi tidak ada berkas yang keluar tanpa jejak.
        """)
public class DownloadLogController {

    @Autowired
    private DownloadLogService downloadLogService;

    @GetMapping()
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Daftar unduhan, terbaru lebih dulu", description = """
            Mengembalikan daftar berhalaman, diurutkan dari yang paling baru.

            **Cara tercepat:** tekan Execute tanpa mengisi apa pun — 20 unduhan terbaru.

            **Contoh pemakaian:**
            - Satu bulan tertentu → `from` = `2026-08-01`, `to` = `2026-08-31`
            - Sejak tanggal tertentu sampai sekarang → isi `from` saja, biarkan `to` kosong

            **Perhatikan:** `to` bersifat inklusif — mengisinya dengan tanggal hari ini ikut
            memuat unduhan yang terjadi hari ini.

            **Jenis akses.** `accessType` memisahkan dua peristiwa yang tabel ini catat
            bersama: `DOWNLOAD` untuk berkas yang benar-benar diunduh setelah menyetujui
            syarat pemakaian, dan `PREVIEW` untuk berkas yang hanya dibuka di peramban.
            Dikosongkan berarti keduanya.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Log unduhan berhasil diambil", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "Tanggal akhir mendahului tanggal awal, atau jenis akses tidak dikenal", content = @Content),
            @ApiResponse(responseCode = "403", description = "Bukan ADMIN", content = @Content)
    })
    public ResponseEntity<Page<DownloadLogResponse>> index(@CurrentUser User user,
            @Parameter(description = "Halaman ke berapa, dimulai dari 0", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Jumlah baris per halaman, maksimum 200", example = "20") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Tanggal awal, format YYYY-MM-DD. Boleh dikosongkan.", example = "2026-08-01") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Tanggal akhir, inklusif. Boleh dikosongkan.", example = "2026-08-31") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Jenis akses: DOWNLOAD atau PREVIEW. Kosongkan untuk keduanya.", example = "DOWNLOAD") @RequestParam(required = false) String accessType) {
        return ResponseEntity.ok(
                downloadLogService.getAll(user, page, size, from, to, accessType));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Ekspor log unduhan sebagai CSV", description = """
            Mengunduh seluruh baris dalam rentang tanggal sebagai satu berkas CSV.

            **Berkas ini memuat data pribadi** — nama, email, divisi, dan alamat IP karyawan.
            Karena itu ekspornya sendiri dicatat di `GET /api/v1/audit-logs`: "siapa yang membawa
            log ini keluar dari sistem" adalah persis pertanyaan yang harus bisa dijawab
            belakangan. Log yang dibuat untuk menelusuri kebocoran tidak boleh justru menjadi jalan
            paling mudah membuatnya.

            Dibatasi 50.000 baris per ekspor. Persempit rentang tanggalnya bila hasilnya terpotong.

            `accessType` berlaku sama seperti di daftar, dan memang harus: berkas yang
            diekspor mesti berisi persis apa yang sedang dilihat di layar, bukan seluruh
            tabel.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Berkas CSV"),
            @ApiResponse(responseCode = "400", description = "Tanggal akhir mendahului tanggal awal", content = @Content),
            @ApiResponse(responseCode = "403", description = "Bukan ADMIN", content = @Content)
    })
    public ResponseEntity<String> export(@CurrentUser User user,
            @Parameter(description = "Tanggal awal, format YYYY-MM-DD.") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Tanggal akhir, inklusif.") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Jenis akses: DOWNLOAD atau PREVIEW. Kosongkan untuk keduanya.", example = "DOWNLOAD") @RequestParam(required = false) String accessType) {

        String csv = downloadLogService.exportCsv(user, from, to, accessType);
        String names = "log-unduhan-" + LocalDate.now() + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + names + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }
}
