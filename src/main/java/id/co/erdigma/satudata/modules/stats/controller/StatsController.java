package id.co.erdigma.satudata.modules.stats.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import id.co.erdigma.satudata.modules.stats.dto.StatsResponse;
import id.co.erdigma.satudata.modules.stats.service.StatsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/stats")
@Tag(name = "5. Statistik", description = """
        Angka ringkas untuk bilah statistik di beranda portal.
        """)
public class StatsController {
    @Autowired
    private StatsService statsService;

    @GetMapping()
    @Operation(summary = "Angka ringkas seluruh portal", description = """
            Jumlah dataset, divisi penerbit, total kunjungan, total unduhan, dan total panggilan
            API — dihitung langsung dari database saat dipanggil, bukan nilai tetap.

            Tanpa parameter apa pun; langsung Execute.

            Nama endpoint ini sudah jamak sejak awal karena `stats` memang bentuk jamak dari
            *statistic*, bukan pengecualian dari konvensi.
            """)
    @ApiResponse(responseCode = "200", description = "Statistik berhasil dihitung", useReturnTypeSchema = true)
    public ResponseEntity<StatsResponse> index() {
        return ResponseEntity.ok(statsService.getStats());
    }
}
