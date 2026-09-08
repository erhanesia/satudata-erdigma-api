package id.co.erdigma.satudata.modules.user.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import id.co.erdigma.satudata.annotation.CurrentUser;
import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.IdPrefix;
import id.co.erdigma.satudata.modules.user.dto.UserAdminResponse;
import id.co.erdigma.satudata.modules.user.dto.UserRoleUpdateRequest;
import id.co.erdigma.satudata.modules.user.service.UserAdminService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.StringToClassMapItem;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

/**
 * Manajemen pengguna portal.
 *
 * `@PreAuthorize` dipasang di tingkat kelas, bukan per method: seluruh isi
 * controller ini punya syarat akses yang sama, dan menaruhnya sekali membuat
 * tidak ada method baru yang bisa lolos karena anotasinya lupa disalin.
 *
 * <p><b>Kunci-mati mungkin terjadi, dan itu disengaja.</b> Larangan mengubah
 * peran sendiri (lihat {@link UserAdminService#ubahPeran}) hanya mencegah
 * seseorang mengunci <i>dirinya sendiri</i> — bukan mencegah kunci-mati sama
 * sekali. Admin A tetap bisa menurunkan admin B (yang tingkat izin HRIS-nya
 * juga ADMIN) ke STAFF; B kehilangan gerbang panel ini secara permanen, dan
 * kalau A kelak berhenti jadi admin, tidak tersisa satu pun admin warisan
 * HRIS yang bisa memulihkannya lewat UI. Menurunkan admin HRIS memang aksi
 * yang sah — yang tidak ada hanyalah jalan pulih lewat antarmuka. Satu-
 * satunya pemulihan adalah SQL langsung ke baris yang mau dipulihkan:
 *
 * <pre>{@code
 * UPDATE users SET role='ADMIN', role_override=NULL, role_override_by=NULL, role_override_at=NULL WHERE email='…';
 * }</pre>
 */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('HRIS_ADMIN')")
@RequiredArgsConstructor
@Tag(name = "8. Manajemen Pengguna", description = """
        Melihat pengguna portal dan menunjuk peran mereka.

        **Hanya untuk admin warisan HRIS** — yaitu akun yang `role`-nya di hris-api memang
        `ADMIN`. Admin yang ditunjuk lewat panel ini sendiri **tidak** bisa membuka endpoint di
        sini; kalau bisa, siapa pun yang sekali ditunjuk dapat menunjuk admin baru dan
        pembatasannya tidak berarti apa-apa.

        Peran yang ditunjuk di sini bertahan melewati penyegaran data HRIS. Mengembalikan
        seseorang mengikuti HRIS dilakukan dengan mengirim `role: null`.

        **Efeknya bukan cuma akses ke panel ini.** `DatasetController` sudah menggerbangi
        penerbitan dataset dengan `hasAnyRole('ADMIN','PUBLISHER')` — jadi menunjuk seseorang
        PUBLISHER atau ADMIN di sini juga memberinya hak menerbitkan dataset, dan menurunkannya
        ke STAFF mencabut hak itu juga.
        """)
public class UserAdminController {

    private final UserAdminService userAdminService;

    @GetMapping
    @Operation(summary = "Daftar pengguna portal", description = """
            Pengguna yang **pernah masuk** ke Satu Data. Orang yang belum pernah membuka portal
            belum punya baris di sini dan karenanya belum bisa ditunjuk.

            `q` mencocokkan sebagian nama atau email tanpa peduli besar-kecil huruf. Halaman
            berbasis 0 mengikuti Spring Data, sama seperti `GET /api/v1/datasets`.

            Bedakan dua kolom peran pada hasilnya: `role` adalah peran efektif, `roleOverride`
            berisi nilai hanya bila peran itu ditunjuk manusia.
            """)
    @ApiResponse(responseCode = "200", description = "Daftar pengguna berhasil diambil", useReturnTypeSchema = true)
    public ResponseEntity<Page<UserAdminResponse>> index(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Endpoint ini cuma bisa dijangkau admin warisan HRIS, jadi ini bukan
        // menambal lubang keamanan — tapi tanpa jepitan, size=100000 tetap
        // membangun satu halaman berisi 100 ribu baris.
        int ukuran = Math.min(size, 100);
        return ResponseEntity.ok(
                userAdminService.daftar(q, PageRequest.of(page, ukuran, Sort.by("name").ascending())));
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Tunjuk peran seorang pengguna", description = """
            Menetapkan peran portal seseorang, menahannya dari penyegaran data HRIS.

            Kirim `{"role": null}` untuk mengembalikannya mengikuti HRIS — peran akan dihitung
            ulang dari tingkat izin HRIS terakhir yang tercatat, tanpa memanggil hris-api.

            Peran sendiri tidak bisa diubah — batasan itu hanya mencegah seseorang mengunci
            dirinya sendiri, bukan jaminan selalu ada admin warisan HRIS lain yang tersisa. Lihat
            Javadoc kelas ini untuk kunci-mati yang tetap mungkin dan cara memulihkannya.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Peran berhasil ditunjuk", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "Mencoba mengubah peran sendiri, atau id tidak berbentuk benar", content = @Content(schema = @Schema(type = "object", properties = @StringToClassMapItem(key = "error", value = String.class)))),
            @ApiResponse(responseCode = "403", description = "Bukan admin warisan HRIS", content = @Content(schema = @Schema(type = "object", properties = @StringToClassMapItem(key = "error", value = String.class)))),
            @ApiResponse(responseCode = "404", description = "Pengguna tidak ditemukan", content = @Content(schema = @Schema(type = "object", properties = @StringToClassMapItem(key = "error", value = String.class))))
    })
    public ResponseEntity<UserAdminResponse> ubahPeran(
            @CurrentUser User pemanggil,
            @Parameter(description = "Id pengguna dalam bentuk `usr-<uuid>` seperti yang muncul di GET /api/v1/users. UUID telanjang tanpa awalan juga diterima.", example = "usr-3fa85f64-5717-4562-b3fc-2c963f66afa6", required = true) @PathVariable String id,
            @RequestBody UserRoleUpdateRequest body) {
        return ResponseEntity.ok(
                userAdminService.ubahPeran(pemanggil, IdPrefix.USER.parse(id), body.getRole()));
    }
}
