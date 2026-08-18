package id.co.erdigma.satudata.modules.status.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import id.co.erdigma.satudata.modules.status.dto.StatusResponse;
import id.co.erdigma.satudata.modules.status.service.StatusService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/status")
@Tag(name = "7. Status Layanan", description = """
        Kesehatan tiap komponen portal, untuk halaman Status Produk.

        Tetap tunggal karena ini *singleton resource* — status sistem hanya ada satu, bukan
        koleksi yang bisa dihitung.
        """)
public class StatusController {
    @Autowired
    private StatusService statusService;

    @GetMapping()
    @Operation(summary = "Kesehatan tiap komponen portal", description = """
            Memeriksa setiap komponen secara nyata saat dipanggil — database benar-benar
            di-ping, penyimpanan berkas benar-benar dicek.

            Tanpa parameter apa pun; langsung Execute.

            **Catatan jujur:** komponen "Real-time API" akan selalu melaporkan *Belum tersedia*,
            bukan *Operasional* — karena fitur itu memang belum dibangun. Itu disengaja; melaporkan
            hijau untuk sesuatu yang tidak ada hanya menyesatkan orang yang membaca halaman status.

            Endpoint ini berbeda dari `/actuator/health` bawaan Spring: yang ini memakai bahasa dan
            pengelompokan sesuai desain halaman Status, sedangkan actuator untuk pemantauan mesin.
            """)
    @ApiResponse(responseCode = "200", description = "Status berhasil diperiksa. Kode 200 tidak berarti semua komponen sehat — periksa isi responsnya", useReturnTypeSchema = true)
    public ResponseEntity<StatusResponse> index() {
        return ResponseEntity.ok(statusService.getStatus());
    }
}
