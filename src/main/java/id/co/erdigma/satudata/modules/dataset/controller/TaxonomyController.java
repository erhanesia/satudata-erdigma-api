package id.co.erdigma.satudata.modules.dataset.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import id.co.erdigma.satudata.modules.dataset.dto.FormatResponse;
import id.co.erdigma.satudata.modules.dataset.dto.TopicResponse;
import id.co.erdigma.satudata.modules.dataset.service.TaxonomyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "3. Taksonomi", description = """
        Daftar nilai untuk mengisi penyaring di `GET /api/v1/datasets`.

        Panggil salah satu endpoint di sini lebih dulu kalau ingin tahu nilai apa saja yang sah
        untuk parameter `topics` dan `formats` — jangan menebak, karena nilai yang tidak dikenal
        hanya menghasilkan daftar kosong tanpa pesan galat.
        """)
public class TaxonomyController {
    @Autowired
    private TaxonomyService taxonomyService;

    @GetMapping("/topics")
    @Operation(summary = "Daftar seluruh topik", description = """
            Delapan topik yang dipakai mengelompokkan dataset, sesuai chip penyaring di halaman
            Datasets portal.

            Nilai `name` dari sini yang diisikan ke parameter `topics` pada
            `GET /api/v1/datasets`. Contoh: `Keuangan`, `Operasional`.

            Tidak perlu login.
            """)
    @ApiResponse(responseCode = "200", description = "Daftar topik berhasil diambil", useReturnTypeSchema = true)
    public ResponseEntity<List<TopicResponse>> indexTopic() {
        return ResponseEntity.ok(taxonomyService.getAllTopic());
    }

    @GetMapping("/formats")
    @Operation(summary = "Daftar seluruh format berkas", description = """
            Format berkas yang tersedia di katalog: CSV, XLSX, GEOJSON, KML, dan API.

            Nilai `name` dari sini yang diisikan ke parameter `formats` pada
            `GET /api/v1/datasets`.

            Catatan: `API` bukan berkas — itu penanda dataset yang dialirkan langsung tanpa
            unduhan. Dan **PDF belum ada di daftar ini**, padahal ada dokumen PDF di berkas
            proyek; itu celah yang masih perlu diputuskan tim.

            Tidak perlu login.
            """)
    @ApiResponse(responseCode = "200", description = "Daftar format berhasil diambil", useReturnTypeSchema = true)
    public ResponseEntity<List<FormatResponse>> indexFormat() {
        return ResponseEntity.ok(taxonomyService.getAllFormat());
    }
}
