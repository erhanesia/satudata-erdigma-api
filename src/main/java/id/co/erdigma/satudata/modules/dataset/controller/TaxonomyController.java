package id.co.erdigma.satudata.modules.dataset.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import id.co.erdigma.satudata.enums.HrisJobLevel;
import id.co.erdigma.satudata.modules.dataset.dto.FormatResponse;
import id.co.erdigma.satudata.modules.dataset.dto.TopicResponse;
import id.co.erdigma.satudata.modules.dataset.service.TaxonomyService;
import id.co.erdigma.satudata.modules.user.port.hris.HrisDirectoryClient;
import id.co.erdigma.satudata.modules.user.port.hris.HrisRefResponse;

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
    @Autowired
    private HrisDirectoryClient hrisDirectoryClient;

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

    @GetMapping("/job-levels")
    @Operation(summary = "Daftar jenjang jabatan", description = """
            Dua belas jenjang jabatan milik HRIS, dipakai membatasi siapa boleh melihat sebuah
            dataset. Urutannya dari yang paling senior, bukan menurut abjad.

            **Nilai dari sini yang diisikan ke `ruleValue`** pada aturan bertipe `JOB_LEVEL` —
            dikirim apa adanya, mis. `Senior Manager`, bukan `SENIOR_MANAGER`. Bentuk itu yang
            dipakai HRIS pada balasan `/me`, dan karenanya juga yang tersimpan di kolom
            `users.job_level` yang dibandingkan.

            Daftar ini TETAP, tidak diambil dari HRIS saat dipanggil. Kedua belas nilainya ada di
            kode hris-api sebagai enum, bukan di tabel, jadi tidak bisa berubah tanpa deploy ulang
            HRIS — memanggil API untuk sesuatu yang tetap hanya menambah titik gagal.

            Menggantikan `GET /api/v1/positions` versi lama, yang mengembalikan sembilan label
            karangan dari berkas desain. Lihat changeset 47.

            Tidak perlu login.
            """)
    @ApiResponse(responseCode = "200", description = "Daftar jenjang jabatan berhasil diambil", useReturnTypeSchema = true)
    public ResponseEntity<List<String>> indexJobLevel() {
        return ResponseEntity.ok(HrisJobLevel.labels());
    }

    @GetMapping("/positions")
    @Operation(summary = "Daftar posisi dari HRIS", description = """
            Diteruskan ke hris-api saat dipanggil, TIDAK disalin ke database portal ini.

            Daftar posisi HRIS berisi puluhan baris dan berubah tanpa memberi tahu siapa pun.
            Menyalinnya mengulangi persoalan yang sudah terjadi pada divisi: salinan yang dibuat
            hari ini sudah tidak cocok lagi keesokan harinya.

            **Nilai `id` dari sini yang diisikan ke `ruleValue`** pada aturan bertipe `POSITION` —
            bukan namanya. Nama posisi di HRIS memuat salah ketik yang suatu saat diperbaiki, dan
            pembatasan berbasis nama akan putus diam-diam begitu itu terjadi.

            Baris uji coba milik HRIS (`DUMMY DELETE`, `Test Baru`, `Finance Baru`) disaring di
            sini supaya tidak muncul di pemilih akses.

            Memakai token pemanggil, jadi HRIS menilai izinnya persis seperti saat orang itu
            membuka HRIS sendiri.
            """)
    @ApiResponse(responseCode = "200", description = "Daftar posisi berhasil diambil", useReturnTypeSchema = true)
    public ResponseEntity<List<HrisRefResponse.Item>> indexPosition(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "100") int size) {
        return ResponseEntity.ok(hrisDirectoryClient.positions(search, size));
    }

    @GetMapping("/employees")
    @Operation(summary = "Cari karyawan di HRIS", description = """
            Diteruskan ke hris-api saat dipanggil, untuk menunjuk orang tertentu sebagai yang
            boleh melihat sebuah dataset.

            **Parameter `search` WAJIB diisi.** Ada ratusan karyawan, dan memuat semuanya untuk
            sebuah pemilih berarti mengirim daftar yang tidak akan dibaca siapa pun sampai habis.
            Mewajibkan kata kunci memaksa antarmuka menampilkan kotak pencarian, dan itu memang
            satu-satunya cara memakai daftar sebesar ini.

            **Nilai `id` dari sini yang diisikan ke `ruleValue`** pada aturan bertipe `EMPLOYEE`.

            Memakai token pemanggil, jadi HRIS menilai izinnya persis seperti saat orang itu
            membuka HRIS sendiri.
            """)
    @ApiResponse(responseCode = "200", description = "Hasil pencarian karyawan", useReturnTypeSchema = true)
    public ResponseEntity<List<HrisRefResponse.Item>> searchEmployee(
            @RequestParam String search,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(hrisDirectoryClient.employees(search, size));
    }

    @GetMapping("/employees/{id}")
    @Operation(summary = "Nama satu karyawan HRIS", description = """
            Menerjemahkan satu id karyawan menjadi namanya.

            Dipakai antarmuka untuk menampilkan aturan bertipe `EMPLOYEE` yang sudah tersimpan.
            Yang disimpan di `dataset_access_rule` adalah UUID — nama posisi dan karyawan di HRIS
            memuat salah ketik yang suatu saat diperbaiki, dan pembatasan berbasis nama akan putus
            diam-diam begitu itu terjadi. Konsekuensinya nama harus dicari saat ditampilkan, dan
            inilah jalurnya.

            **Jangan dipanggil per baris pada halaman daftar.** Kolom akses di panel admin sengaja
            hanya menampilkan jumlah, supaya lima puluh baris tidak berubah jadi lima puluh
            panggilan.

            Menjawab 404 kalau HRIS tidak mengenali id-nya — wajar terjadi kalau karyawannya sudah
            dihapus setelah aturannya dibuat.
            """)
    @ApiResponse(responseCode = "200", description = "Karyawan ditemukan", useReturnTypeSchema = true)
    public ResponseEntity<HrisRefResponse.Item> getEmployee(@PathVariable UUID id) {
        return hrisDirectoryClient.employee(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
