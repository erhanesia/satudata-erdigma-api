package id.co.erdigma.satudata.modules.audit.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.annotation.CurrentUser;
import id.co.erdigma.satudata.modules.audit.dto.AuditLogResponse;
import id.co.erdigma.satudata.modules.audit.service.AuditLogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "8. Log audit", description = """
        Siapa melakukan apa terhadap dataset mana, dan kapan.

        Mengisi panel **Aktivitas terakhir** di dasbor admin dan tab **Log audit** di halaman Log.

        **Hanya ADMIN.** Jejak audit memuat nama orang beserta tindakannya; itu bukan bacaan
        untuk semua karyawan.

        **Yang perlu diketahui tentang isinya sekarang:** tindakan yang benar-benar dicatat kode
        baru `CREATE`, yaitu saat dataset diterbitkan lewat `POST /api/v1/datasets`. Nilai lain
        (`UPDATE`, `SUBMIT`, `PUBLISH`, `REJECT`, `ARCHIVE`, `DELETE`) sudah ada di enum dan
        muncul pada data dummy, tapi belum ada alur yang memancarkannya — alur tinjauan dan
        pengarsipan memang belum dibuat.
        """)
public class AuditLogController {

    @Autowired
    private AuditLogService auditLogService;

    @GetMapping()
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Daftar jejak audit, terbaru lebih dulu", description = """
            Mengembalikan daftar berhalaman, diurutkan dari yang paling baru.

            **Cara tercepat:** tekan Execute tanpa mengisi apa pun — 20 baris terbaru.

            **Contoh pemakaian:**
            - Riwayat satu dataset → isi `slug` dengan `penjualan-bulanan`
            - Ambil 6 baris untuk kartu dasbor → isi `size` dengan `6`

            **Membaca hasilnya:** `action` adalah kata tindakannya, `objectLabel` judul dataset
            saat tindakan itu terjadi (bukan judul terbarunya), dan `detail` keterangan singkat
            yang boleh kosong. `number` adalah halaman saat ini — **dimulai dari 0**, bukan 1.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Jejak audit berhasil diambil", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "403", description = "Bukan ADMIN", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<Page<AuditLogResponse>> index(@CurrentUser User user,
            @Parameter(description = "Halaman ke berapa, dimulai dari 0", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Jumlah baris per halaman, maksimum 200", example = "20") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Saring ke satu dataset saja, diisi slug-nya", example = "penjualan-bulanan") @RequestParam(required = false) String slug) {
        return ResponseEntity.ok(auditLogService.getAll(user, page, size, slug));
    }
}
