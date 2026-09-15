package id.co.erdigma.satudata.modules.dataset.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetResource;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetRow;
import id.co.erdigma.satudata.modules.dataset.entity.Format;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetResourceRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRowRepository;
import id.co.erdigma.satudata.modules.dataset.repository.FormatRepository;
import id.co.erdigma.satudata.modules.division.repository.DivisionRepository;

/**
 * Isi CSV yang diimpor terbaca kembali persis sama.
 *
 * <h2>Kenapa perlu</h2>
 *
 * Baris impor ditulis lewat JDBC: JSON-nya disusun aplikasi lalu dikirim
 * sebagai teks. Kesalahan penyusunan JSON atau pengikatan parameter tidak
 * selalu berakhir galat; bisa juga berupa nilai yang diam-diam berubah. Nilai
 * yang dipakai di sini yang paling rawan: koma dan tanda kutip di dalam nilai,
 * garis miring terbalik, tab, huruf non-Latin, emoji, sel kosong, dan baris
 * yang kolomnya kurang.
 *
 * Barisnya dibuat melewati dua kali ukuran batch importir, supaya potongan
 * batch terakhir yang tidak penuh ikut teruji.
 *
 * Tidak ada nilai berisi baris baru. Importir membaca CSV per baris teks, jadi
 * nilai seperti itu memang belum didukung, baik sebelum maupun sesudah
 * penulisan lewat JDBC.
 *
 * <h2>Kenapa aman dijalankan</h2>
 *
 * Seluruh kelas berada di dalam transaksi yang selalu dibatalkan, dan berkas
 * asalnya hanya baris di tabel: tidak ada yang ditulis ke penyimpanan.
 */
@SpringBootTest(properties = "satudata.storage.provider=LOCAL")
@Transactional
class DatasetImportRoundTripTest {

    private static final int FILLER_ROWS = 2500;

    @Autowired
    private DatasetImportService datasetImportService;
    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private DatasetResourceRepository datasetResourceRepository;
    @Autowired
    private DatasetRowRepository datasetRowRepository;
    @Autowired
    private FormatRepository formatRepository;
    @Autowired
    private DivisionRepository divisionRepository;

    @TempDir
    private Path tempDir;

    private Dataset dataset;
    private DatasetResource resource;

    @BeforeEach
    void seed() {
        dataset = new Dataset();
        dataset.setSlug("uji-impor-bolak-balik-" + UUID.randomUUID());
        dataset.setTitle("Uji Impor Bolak-balik");
        dataset.setDivision(divisionRepository.findAll().get(0));
        datasetRepository.save(dataset);

        Format csv = formatRepository.findAllByDeletedAtIsNullOrderBySortOrderAsc().stream()
                .filter(f -> "CSV".equals(f.getName()))
                .findFirst()
                .orElseThrow();

        /*
          Dataset dan berkasnya disimpan tanpa flush, persis seperti unggahan
          sungguhan: keduanya didaftarkan lewat JPA tepat sebelum isinya
          dibaca. Karena itu tes ini juga gagal kalau penulis baris lupa
          melakukan flush lebih dulu, sebab kunci asing baris ke dataset dan
          ke berkas akan menolak.
        */
        resource = new DatasetResource();
        resource.setDataset(dataset);
        resource.setFormat(csv);
        resource.setLabel("Nilai Rawan");
        resource.setFileName(dataset.getSlug() + ".csv");
        resource.setContentType("text/csv");
        resource.setStorageProvider("LOCAL");
        resource.setStorageKey("dataset/" + dataset.getSlug() + "/" + dataset.getSlug() + ".csv");
        resource.setSizeBytes(1024);
        datasetResourceRepository.save(resource);
    }

    private static Map<String, Object> row(Object nama, Object catatan, Object angka) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nama", nama);
        data.put("catatan", catatan);
        data.put("angka", angka);
        return data;
    }

    @Test
    @DisplayName("nilai CSV yang paling rawan terbaca kembali tanpa berubah")
    void trickyValuesSurviveTheRoundTrip() throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append("nama,catatan,angka\n");
        csv.append("\"Budi, S.Kom\",\"Dia bilang \"\"halo\"\"\",007\n");
        csv.append("王小明,مرحبا 🙂,\n");
        csv.append("C:\\data\\new,tab\tdi tengah,null\n");
        csv.append("hanya satu\n");
        for (int i = 1; i <= FILLER_ROWS; i++) {
            csv.append("baris-").append(i).append(",isi,").append(i).append('\n');
        }
        Path source = tempDir.resolve("nilai-rawan.csv");
        Files.writeString(source, csv, StandardCharsets.UTF_8);

        datasetImportService.importCsv(dataset, source, "text/csv", resource);

        int expectedRows = FILLER_ROWS + 4;
        List<DatasetRow> rows = datasetRowRepository
                .findAllByResourceIdOrderByRowNumberAsc(resource.getId(), PageRequest.of(0, expectedRows + 1))
                .getContent();

        assertThat(rows).hasSize(expectedRows);
        assertThat(rows).extracting(DatasetRow::getRowNumber)
                .as("nomor baris berurutan tanpa celah, termasuk di batas batch")
                .containsExactlyElementsOf(LongStream.rangeClosed(1, expectedRows).boxed().toList());
        assertThat(rows).allSatisfy(r -> assertThat(r.getDatasetId()).isEqualTo(dataset.getId()));

        assertThat(rows.get(0).getData()).isEqualTo(row("Budi, S.Kom", "Dia bilang \"halo\"", "007"));
        assertThat(rows.get(1).getData()).isEqualTo(row("王小明", "مرحبا 🙂", ""));
        assertThat(rows.get(2).getData()).isEqualTo(row("C:\\data\\new", "tab\tdi tengah", "null"));
        assertThat(rows.get(3).getData()).isEqualTo(row("hanya satu", null, null));
        assertThat(rows.get(expectedRows - 1).getData())
                .isEqualTo(row("baris-" + FILLER_ROWS, "isi", String.valueOf(FILLER_ROWS)));

        assertThat(resource.getRowCount()).isEqualTo(expectedRows);
    }
}
