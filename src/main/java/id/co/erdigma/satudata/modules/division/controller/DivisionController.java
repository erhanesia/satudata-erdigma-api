package id.co.erdigma.satudata.modules.division.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import id.co.erdigma.satudata.modules.division.dto.DivisionResponse;
import id.co.erdigma.satudata.modules.division.service.DivisionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/divisions")
@Tag(name = "2. Divisi", description = """
        Divisi perusahaan sebagai **penerbit dan penanggung jawab dataset**.

        Istilah teknisnya di CKAN adalah *agency* atau *organization*, merujuk instansi penerbit
        data. Di portal internal ini penerbitnya divisi, jadi itu nama yang dipakai.

        Sejak changeset 42 isinya adalah **team** di sistem HRIS, bukan daftar karangan.
        Jembatannya kolom `hrisTeamId`, dan dari situ pula divisi seorang pengguna
        ditentukan saat login.

        Perhatikan bahwa yang dipetakan adalah `team`, **bukan** `departement`. Di HRIS
        `departement` berarti badan usaha — Gemilang Multazam, Erha Idea Cipta Karsa, dan
        empat lainnya — sedangkan unit kerja yang di portal ini disebut divisi adalah
        `team`. Keduanya bukan susunan bertingkat.
        """)
public class DivisionController {
    @Autowired
    private DivisionService divisionService;

    @GetMapping()
    @Operation(summary = "Daftar seluruh divisi", description = """
            Seluruh divisi beserta kode, nama, warna avatar, dan jumlah `downloads`.
            Isinya 32 team Erdigma dari hris-api.

            Urutannya menurun berdasarkan `downloads`. Sebelum changeset 40 urutannya
            memakai `apiCalls`; kolom itu dicabut karena penghitungnya ikut hilang
            bersama fitur API key, sehingga peringkat halaman ditentukan angka yang
            tidak pernah bergerak.

            **Nilai `code` dari sini yang diisikan ke parameter `divisions`** pada
            `GET /api/v1/datasets` untuk menyaring dataset per divisi penerbit —
            misalnya `DIT` untuk Data & IT, `FAT` untuk Finance Accounting & Tax,
            atau `SCEW` untuk Social Commerce Eyebost & Waji.

            Kodenya milik portal ini sendiri, disusun sebagai singkatan nama karena HRIS
            hanya menyimpan id dan nama. Ambil daftar terkininya dari endpoint ini,
            jangan ditulis tetap di sisi pemanggil.

            Mengisi parameter itu dengan nama panjang seperti `Data & IT` tidak akan
            menyaring apa pun — yang dipakai kodenya.
            """)
    @ApiResponse(responseCode = "200", description = "Daftar divisi berhasil diambil", useReturnTypeSchema = true)
    public ResponseEntity<List<DivisionResponse>> index() {
        return ResponseEntity.ok(divisionService.getAll());
    }
}
