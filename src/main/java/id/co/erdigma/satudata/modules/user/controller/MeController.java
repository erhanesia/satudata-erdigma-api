package id.co.erdigma.satudata.modules.user.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import id.co.erdigma.satudata.annotation.CurrentUser;
import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.modules.user.dto.UserResponse;
import id.co.erdigma.satudata.modules.user.service.MeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.StringToClassMapItem;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "0. Identitas saya", description = """
        Siapa saya menurut server. **Panggil ini lebih dulu untuk memastikan Authorize berhasil**
        — kalau di sini sudah menjawab 200, kredensial Anda benar dan endpoint lain pasti bisa
        dipanggil.

        Tetap tunggal (`/me`, bukan `/mes`) karena ini *singleton resource*: hanya ada satu, dan
        isinya bergantung siapa yang memanggil. Konvensi jamak berlaku untuk koleksi, bukan untuk
        sumber daya tunggal semacam ini.
        """)
public class MeController {
    @Autowired
    private MeService meService;

    @GetMapping()
    @Operation(summary = "Profil pengguna yang sedang login", description = """
            Identitas pemanggil: nama, email, jabatan, divisi, peran portal, dan tingkat izin
            hasil hitungan HRIS.

            Dipakai portal untuk mengisi pil identitas di header setiap halaman.

            **Cara mencoba di Swagger:** tekan Authorize, tempel token akses Cognito di
            `bearerAuth`, lalu Execute di sini. Nama yang muncul harus nama pemilik token.

            | Keadaan | Hasil |
            |---|---|
            | Token karyawan aktif | 200, peran sesuai jenjang jabatannya di HRIS |
            | Token yang pemiliknya bukan karyawan aktif | **401** — hris-api menolak identitas itu |
            | *(tanpa Authorize)* | **401** |

            Perhatikan `role` (peran di portal) berbeda dari `hrisPermissionLevel` (tingkat izin
            dari sistem HRIS). Keduanya sengaja dipisah karena sumbernya berbeda.

            ⚠️ Peran portal saat ini **belum menegakkan apa pun** — belum ada satu pun
            `@PreAuthorize` di kode. Semua pengguna terautentikasi bisa memanggil semua endpoint.
            Itu masih aman karena seluruh endpoint bersifat baca atau sudah dibatasi per-pengguna,
            tapi penegakan role wajib ada sebelum sisi admin dibangun.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil berhasil diambil — berarti autentikasi Anda benar", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "401", description = "Belum Authorize, atau identitas tidak dikenal", content = @Content(schema = @Schema(type = "object", properties = @StringToClassMapItem(key = "message", value = String.class)))),
            @ApiResponse(responseCode = "403", description = "Karyawan sudah resign sehingga aksesnya ditolak", content = @Content(schema = @Schema(type = "object", properties = @StringToClassMapItem(key = "error", value = String.class))))
    })
    public ResponseEntity<UserResponse> index(@CurrentUser User user) {
        return ResponseEntity.ok(meService.toResponse(user));
    }
}
