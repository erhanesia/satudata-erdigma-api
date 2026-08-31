package id.co.erdigma.satudata.modules.dataset.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import id.co.erdigma.satudata.enums.JobPosition;
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
    @Operation(summary = "Daftar seluruh jenis berkas", description = """
            Jenis berkas yang dipakai katalog ini: **CSV, XLSX, PDF, dan DOCX**.

            Nilai `name` dari sini yang diisikan ke parameter `formats` pada
            `GET /api/v1/datasets`.

            Daftarnya dirapikan pada changeset 00026. Sebelumnya memuat GEOJSON dan KML yang tidak
            dipakai, serta `API` yang sebenarnya bukan jenis berkas melainkan cara pengiriman —
            keadaan itu sudah punya penandanya sendiri, yaitu `realtime` pada dataset.

            Tidak perlu login.
            """)
    @ApiResponse(responseCode = "200", description = "Daftar format berhasil diambil", useReturnTypeSchema = true)
    public ResponseEntity<List<FormatResponse>> indexFormat() {
        return ResponseEntity.ok(taxonomyService.getAllFormat());
    }

    @GetMapping("/positions")
    @Operation(summary = "Daftar posisi jabatan", description = """
            Sembilan posisi jabatan yang bisa dipakai membatasi siapa boleh melihat sebuah
            dataset — isi penyaring "Akses posisi" dan isian pada form terbitkan dataset.

            **Pembatasannya berlaku sungguhan.** Dataset yang diberi tag hanya bisa dibuka,
            dibaca isinya, dan diunduh oleh pemilik posisi tersebut — selain ADMIN dan
            pengunggahnya sendiri. Dataset tanpa tag terbuka untuk seluruh karyawan.

            Posisi setiap pengguna disimpan di kolom `users.access_position`, milik portal ini.
            HRIS sendiri menyimpan dua sumbu berbeda: `job_level` (enum 12 nilai) dan `position`
            (teks bebas seperti "Project Manager Data & IT"). Tak satu pun cocok satu-satu dengan
            sembilan label di bawah, jadi kolomnya dibuat sendiri dan nanti diisi dari HRIS lewat
            pemetaan yang ditulis sekali — pola yang sama dengan `hris_permission_level`.

            Karena itu daftar di bawah masih tetap (hard-coded), bukan dibaca dari tabel.

            Tidak perlu login.
            """)
    @ApiResponse(responseCode = "200", description = "Daftar posisi berhasil diambil", useReturnTypeSchema = true)
    public ResponseEntity<List<String>> indexPosition() {
        return ResponseEntity.ok(JobPosition.labels());
    }
}
