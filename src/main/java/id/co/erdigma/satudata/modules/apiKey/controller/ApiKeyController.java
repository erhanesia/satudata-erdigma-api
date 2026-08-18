package id.co.erdigma.satudata.modules.apiKey.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import id.co.erdigma.satudata.annotation.CurrentUser;
import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.IdPrefix;
import id.co.erdigma.satudata.modules.apiKey.dto.ApiKeyCreatedResponse;
import id.co.erdigma.satudata.modules.apiKey.dto.ApiKeyRequestCreateDTO;
import id.co.erdigma.satudata.modules.apiKey.dto.ApiKeyResponse;
import id.co.erdigma.satudata.modules.apiKey.dto.ApiKeyRevealResponse;
import id.co.erdigma.satudata.modules.apiKey.service.ApiKeyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.StringToClassMapItem;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/api-keys")
@Tag(name = "6. API Key", description = """
        Kunci akses mesin, untuk halaman Dasbor Akun di portal.

        Seluruh endpoint di sini **hanya melihat milik pengguna yang sedang login** — key milik
        orang lain tidak pernah terlihat maupun bisa dicabut.

        **Cara nilai kunci disimpan.** Sejak changeset 00022, kunci disimpan **terenkripsi
        AES-256-GCM** di samping hash SHA-256-nya, sehingga tombol "Tampilkan" dan "Salin" di
        desain bisa dilayani lewat `GET /api/v1/api-keys/{id}/reveal`.

        ⚠️ Konsekuensinya nyata dan sudah disepakati: **siapa pun yang memegang database DAN
        rahasia enkripsi bisa membaca seluruh API key.** Rahasianya wajib datang dari env var
        `APIKEY_ENCRYPTION_SECRET`; aplikasi menolak start bila kosong. Jangan pernah menaruhnya
        di berkas yang ikut ter-commit, dan rotasi rahasia berarti seluruh kunci lama tidak lagi
        bisa didekripsi.

        Tiga kunci yang dibuat sebelum perubahan ini tidak punya ciphertext — endpoint reveal
        menjawabnya 400 dengan penjelasan.

        ⚠️ Jalur autentikasi memakai API key **belum dibangun** di sisi server. Key bisa dibuat,
        ditampilkan, dan dicabut, tapi belum ada endpoint yang menerimanya sebagai kredensial.
        """)
public class ApiKeyController {
    @Autowired
    private ApiKeyService apiKeyService;

    @GetMapping()
    @Operation(summary = "Daftar API key milik saya", description = """
            Seluruh key milik pengguna yang sedang login, beserta nama, waktu pembuatan, dan
            waktu pemakaian terakhir.

            **Nilai key-nya tidak ikut di sini** — yang tampil hanya bentuk tersamar
            (`masked`). Untuk nilai penuh, panggil `GET /api/v1/api-keys/{id}/reveal` satu kunci
            pada satu waktu, supaya nilai kunci tidak berhamburan di respons daftar.
            """)
    @ApiResponse(responseCode = "200", description = "Daftar key berhasil diambil. Bisa berupa daftar kosong", useReturnTypeSchema = true)
    public ResponseEntity<List<ApiKeyResponse>> index(@CurrentUser User user) {
        return ResponseEntity.ok(apiKeyService.getAll(user));
    }

    @PostMapping()
    @Operation(summary = "Buat API key baru", description = """
            Membuat kunci akses baru untuk pengguna yang sedang login.

            **Cara mengisi:** badan permintaan hanya butuh satu field, `name` — label bebas supaya
            Anda ingat key itu dipakai untuk apa. Contoh:

            ```json
            { "name": "Integrasi dashboard tim Sales" }
            ```

            `plainKey` di respons adalah nilai kuncinya. Nilai itu juga bisa dilihat lagi kapan
            saja lewat `GET /api/v1/api-keys/{id}/reveal`, karena kunci disimpan terenkripsi.

            Ada batas jumlah key aktif per pengguna; kalau tercapai, permintaan ditolak 400 dan
            key lama harus dicabut lebih dulu.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Key dibuat. Nilai plainKey hanya muncul di respons ini", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "Batas key aktif tercapai, atau nama tidak diisi", content = @Content(schema = @Schema(type = "object", properties = @StringToClassMapItem(key = "error", value = String.class))))
    })
    public ResponseEntity<ApiKeyCreatedResponse> create(@CurrentUser User user,
            @Valid @RequestBody ApiKeyRequestCreateDTO body) {
        return ResponseEntity.ok(apiKeyService.create(user, body));
    }

    @GetMapping("/{id}/reveal")
    @Operation(summary = "Tampilkan nilai API key seutuhnya", description = """
            Melayani tombol **Tampilkan** di Dasbor Akun — mengembalikan nilai kunci hasil
            dekripsi.

            Hanya kunci milik sendiri yang bisa diungkap. Kunci milik orang lain dijawab 404
            seolah tidak ada, bukan 403 — supaya keberadaannya pun tidak bocor.

            Kunci yang sudah **dicabut** tetap boleh diungkap. Itu disengaja: pemiliknya berhak
            tahu nilai apa yang harus dicari dan dibersihkan dari skrip yang mungkin masih
            memakainya.

            **Tiga kunci yang dibuat sebelum fitur ini ada tidak punya ciphertext** dan akan
            dijawab 400 dengan penjelasan — bukan galat yang membingungkan.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nilai kunci berhasil didekripsi", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "Kunci dibuat sebelum penyimpanan terenkripsi ada, jadi nilainya tidak tersimpan", content = @Content(schema = @Schema(type = "object", properties = @StringToClassMapItem(key = "error", value = String.class)))),
            @ApiResponse(responseCode = "404", description = "Kunci tidak ditemukan, atau bukan milik Anda", content = @Content(schema = @Schema(type = "object", properties = @StringToClassMapItem(key = "error", value = String.class))))
    })
    public ResponseEntity<ApiKeyRevealResponse> reveal(@CurrentUser User user,
            @Parameter(description = "Id kunci dalam bentuk `key-<uuid>` seperti yang muncul di GET /api/v1/api-keys. UUID telanjang tanpa awalan juga diterima.", example = "key-3fa85f64-5717-4562-b3fc-2c963f66afa6", required = true) @PathVariable String id) {
        return ResponseEntity.ok(apiKeyService.reveal(user, IdPrefix.API_KEY.parse(id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cabut API key", description = """
            Menonaktifkan satu key milik sendiri secara permanen. Tidak bisa dibatalkan.

            **Perhatikan:** di sini path parameternya **UUID**, berbeda dengan endpoint dataset yang
            memakai slug. Alasannya API key tidak punya nama publik dan tidak pernah muncul di URL
            yang dibagikan — jadi identitas internalnya yang dipakai.

            Ambil nilai `id` dari `GET /api/v1/api-keys`.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Key berhasil dicabut"),
            @ApiResponse(responseCode = "404", description = "Key tidak ditemukan, atau bukan milik Anda", content = @Content(schema = @Schema(type = "object", properties = @StringToClassMapItem(key = "error", value = String.class))))
    })
    public ResponseEntity<String> delete(@CurrentUser User user,
            @Parameter(description = "Id kunci dalam bentuk `key-<uuid>` seperti yang muncul di GET /api/v1/api-keys. UUID telanjang tanpa awalan juga diterima.", example = "key-3fa85f64-5717-4562-b3fc-2c963f66afa6", required = true) @PathVariable String id) {
        apiKeyService.revoke(user, IdPrefix.API_KEY.parse(id));
        return ResponseEntity.ok("Success");
    }
}
