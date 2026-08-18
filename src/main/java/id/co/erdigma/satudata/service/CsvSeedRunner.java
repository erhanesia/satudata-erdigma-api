package id.co.erdigma.satudata.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRowRepository;
import id.co.erdigma.satudata.modules.dataset.service.DatasetImportService;

import lombok.extern.slf4j.Slf4j;

/**
 * Mengimpor CSV contoh sekali saat startup, hanya di profil dev dan hanya kalau
 * isinya belum ada — jadi aman dijalankan berulang. Kalau berkasnya tidak
 * ditemukan, aplikasi tetap start dan hanya mencatat peringatan.
 */
@Component
@Profile("dev")
@Slf4j
public class CsvSeedRunner implements ApplicationRunner {

    private static final String DATASET_SLUG = "penjualan-furnitur-2025";

    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private DatasetRowRepository datasetRowRepository;
    @Autowired
    private DatasetImportService datasetImportService;

    @Value("${satudata.seed.csv-path:../../projectFiles/dataset/furniture_10k_FIXED.csv}")
    private String csvPath;

    @Override
    public void run(ApplicationArguments args) {
        Optional<Dataset> found = datasetRepository.findBySlugAndDeletedAtIsNull(DATASET_SLUG);
        if (found.isEmpty()) {
            log.warn("Dataset {} belum ada di database — impor CSV dilewati.", DATASET_SLUG);
            return;
        }
        Dataset dataset = found.get();

        if (datasetRowRepository.countByDatasetId(dataset.getId()) > 0) {
            log.info("Isi dataset {} sudah ada, impor CSV dilewati.", DATASET_SLUG);
            return;
        }

        Path source = Paths.get(csvPath).toAbsolutePath().normalize();
        if (!Files.exists(source)) {
            log.warn("Berkas CSV tidak ditemukan di {} — impor dilewati. "
                    + "Atur lokasinya lewat properti satudata.seed.csv-path.", source);
            return;
        }

        try {
            datasetImportService.importCsv(dataset, source, "text/csv");
        } catch (IOException e) {
            log.error("Gagal mengimpor CSV dari {}", source, e);
        }
    }
}
