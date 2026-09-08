package id.co.erdigma.satudata.modules.user.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import id.co.erdigma.satudata.modules.user.dto.UserAdminResponse;
import id.co.erdigma.satudata.modules.user.service.UserAdminService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

/**
 * Manajemen pengguna portal.
 *
 * `@PreAuthorize` dipasang di tingkat kelas, bukan per method: seluruh isi
 * controller ini punya syarat akses yang sama, dan menaruhnya sekali membuat
 * tidak ada method baru yang bisa lolos karena anotasinya lupa disalin.
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
        return ResponseEntity.ok(
                userAdminService.daftar(q, PageRequest.of(page, size, Sort.by("name").ascending())));
    }
}
