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

        Perhatikan bahwa divisi di sini **bukan** departemen di sistem HRIS. Keduanya sistem
        terpisah; jembatannya kolom `hrisDepartementId`, yang saat ini masih kosong karena
        pemetaannya menunggu daftar departemen dari tim HRIS.
        """)
public class DivisionController {
    @Autowired
    private DivisionService divisionService;

    @GetMapping()
    @Operation(summary = "Daftar seluruh divisi", description = """
            Delapan divisi beserta kode, nama, warna avatar, dan penghitung pemakaian
            (`apiCalls`, `downloads`).

            **Nilai `code` dari sini yang diisikan ke parameter `divisions`** pada
            `GET /api/v1/datasets` untuk menyaring dataset per divisi penerbit:
            `DNA`, `IT`, `PROD`, `SALES`, `FIN`, `OPS`, `HR`, `MKT`.

            Mengisi parameter itu dengan nama panjang seperti `Divisi Penjualan` tidak akan
            menyaring apa pun — yang dipakai kodenya.
            """)
    @ApiResponse(responseCode = "200", description = "Daftar divisi berhasil diambil", useReturnTypeSchema = true)
    public ResponseEntity<List<DivisionResponse>> index() {
        return ResponseEntity.ok(divisionService.getAll());
    }
}
