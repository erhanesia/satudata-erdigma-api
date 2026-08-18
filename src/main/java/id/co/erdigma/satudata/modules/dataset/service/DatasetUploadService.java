package id.co.erdigma.satudata.modules.dataset.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.exception.BusinessValidationException;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetRequestCreateDTO;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetResponse;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetCollection;
import id.co.erdigma.satudata.modules.dataset.entity.Format;
import id.co.erdigma.satudata.modules.dataset.entity.Topic;
import id.co.erdigma.satudata.modules.dataset.helper.SlugGenerator;
import id.co.erdigma.satudata.modules.dataset.repository.CollectionRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;
import id.co.erdigma.satudata.modules.dataset.repository.FormatRepository;
import id.co.erdigma.satudata.modules.dataset.repository.TopicRepository;

import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Menerbitkan dataset baru dari satu berkas CSV yang diunggah.
 *
 * Versi internal untuk pengujian — desain sisi penerbit belum ada, jadi bentuk
 * layarnya masih bisa berubah. Yang sudah pasti dan sengaja dipertahankan
 * adalah pembagian tugasnya: apa yang bisa diverifikasi mesin dihitung sendiri,
 * apa yang butuh pertanggungjawaban manusia diminta dari penerbit.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatasetUploadService {

    /** Sejalan dengan spring.servlet.multipart.max-file-size. */
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private TopicRepository topicRepository;
    @Autowired
    private FormatRepository formatRepository;
    @Autowired
    private CollectionRepository collectionRepository;
    @Autowired
    private DatasetImportService datasetImportService;
    @Autowired
    private DatasetService datasetService;
    @Autowired
    private SlugGenerator slugGenerator;
    @Autowired
    private EntityManager entityManager;

    @Transactional
    public DatasetResponse upload(User user, DatasetRequestCreateDTO body, MultipartFile file) {
        validateFile(file);

        if (user.getDivision() == null) {
            throw new BusinessValidationException(
                    "Akun Anda belum terhubung ke divisi mana pun, sehingga tidak ada yang bisa "
                            + "dicatat sebagai penerbit dataset ini.");
        }

        String slug = resolveSlug(body);

        Dataset dataset = new Dataset();
        dataset.setSlug(slug);
        dataset.setTitle(body.getTitle().trim());
        // Divisi diambil dari akun, bukan dari isian. Penerbit tidak perlu —
        // dan tidak boleh — mengaku mewakili divisi lain.
        dataset.setDivision(user.getDivision());
        dataset.setNotes(trimToNull(body.getNotes()));
        dataset.setDisclaimer(trimToNull(body.getDisclaimer()));
        dataset.setCoverage(trimToNull(body.getCoverage()));
        dataset.setCollection(resolveCollection(body.getCollectionSlug()));
        dataset.setTopics(resolveTopics(body.getTopics()));
        dataset.setFormats(resolveCsvFormat());
        dataset.setLastUpdatedAt(LocalDateTime.now());
        dataset.setFileSize(humanSize(file.getSize()));
        datasetRepository.save(dataset);

        importFile(dataset, file);

        // Kolom dan baris disimpan lewat repository-nya masing-masing, jadi
        // objek Dataset yang masih menempel di persistence context tetap
        // memegang daftar kolom kosong seperti saat dibuat. Tanpa dua baris ini,
        // respons unggah mengembalikan "columns": [] padahal kolomnya sudah ada
        // di database — cacat yang baru terlihat kalau membandingkan respons
        // POST dengan GET setelahnya.
        entityManager.flush();
        entityManager.clear();

        // Dibaca ulang lewat jalur yang sama dengan GET /{slug}, supaya bentuk
        // respons unggah tidak pernah menyimpang dari bentuk yang sudah dipakai
        // front-end.
        return datasetService.getBySlug(slug);
    }

    /**
     * Menyalin unggahan ke berkas sementara, karena {@code DatasetImportService}
     * bekerja dari {@link Path} — bentuk yang sama dipakai importir seed. Berkas
     * sementaranya dihapus apa pun yang terjadi.
     */
    private void importFile(Dataset dataset, MultipartFile file) {
        Path temp = null;
        try {
            temp = Files.createTempFile("satudata-upload-", ".csv");
            file.transferTo(temp);
            datasetImportService.importCsv(dataset, temp,
                    file.getContentType() == null ? "text/csv" : file.getContentType());
        } catch (IOException e) {
            log.error("Gagal mengimpor unggahan untuk dataset {}", dataset.getSlug(), e);
            throw new BusinessValidationException(
                    "Berkas gagal dibaca. Pastikan formatnya CSV dan tidak rusak.");
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException e) {
                    log.warn("Berkas sementara {} tidak terhapus", temp, e);
                }
            }
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessValidationException("Berkas CSV wajib diunggah.");
        }
        String nama = file.getOriginalFilename();
        if (nama == null || !nama.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new BusinessValidationException(
                    "Untuk sekarang hanya berkas .csv yang didukung.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessValidationException(
                    "Ukuran berkas melebihi batas " + humanSize(MAX_BYTES) + ".");
        }
    }

    /**
     * Slug dari penerbit dipakai apa adanya bila sah dan belum terpakai — tidak
     * diam-diam ditambahi angka. Kalau seseorang sengaja mengetik slug tertentu
     * lalu mendapat {@code -2} di belakangnya tanpa diberi tahu, tautan yang
     * sudah terlanjur dibagikan akan menunjuk ke tempat yang salah.
     *
     * Slug yang dibangkitkan sistem justru sebaliknya: penerbit tidak memilihnya,
     * jadi menambahkan angka agar tersedia adalah perilaku yang wajar.
     */
    private String resolveSlug(DatasetRequestCreateDTO body) {
        String diminta = trimToNull(body.getSlug());
        if (diminta == null) {
            return slugGenerator.uniqueSlug(body.getTitle(), datasetRepository::existsBySlug);
        }
        if (!slugGenerator.isValid(diminta)) {
            throw new BusinessValidationException(
                    "Slug \"" + diminta + "\" tidak sah. Pakai huruf kecil, angka, dan tanda "
                            + "hubung saja — misalnya penjualan-furnitur-2025.");
        }
        if (datasetRepository.existsBySlug(diminta)) {
            throw new BusinessValidationException(
                    "Slug \"" + diminta + "\" sudah dipakai. Usulan yang tersedia: "
                            + slugGenerator.uniqueSlug(diminta, datasetRepository::existsBySlug));
        }
        return diminta;
    }

    private DatasetCollection resolveCollection(String slug) {
        String bersih = trimToNull(slug);
        if (bersih == null) {
            return null;
        }
        return collectionRepository.findBySlugAndDeletedAtIsNull(bersih)
                .orElseThrow(() -> new BusinessValidationException(
                        "Koleksi \"" + bersih + "\" tidak ada. Lihat GET /api/v1/collections."));
    }

    private List<Topic> resolveTopics(List<String> nama) {
        if (nama == null || nama.isEmpty()) {
            return new ArrayList<>();
        }
        List<Topic> semua = topicRepository.findAllByDeletedAtIsNullOrderBySortOrderAsc();
        List<Topic> hasil = new ArrayList<>();
        for (String n : nama) {
            String bersih = trimToNull(n);
            if (bersih == null) {
                continue;
            }
            semua.stream()
                    .filter(t -> t.getName().equalsIgnoreCase(bersih))
                    .findFirst()
                    .ifPresentOrElse(hasil::add, () -> {
                        throw new BusinessValidationException(
                                "Topik \"" + bersih + "\" tidak ada. Lihat GET /api/v1/topics.");
                    });
        }
        return hasil;
    }

    private List<Format> resolveCsvFormat() {
        List<Format> hasil = new ArrayList<>();
        formatRepository.findAllByDeletedAtIsNullOrderBySortOrderAsc().stream()
                .filter(f -> "CSV".equals(f.getName()))
                .findFirst()
                .ifPresent(hasil::add);
        return hasil;
    }

    /**
     * Kolom {@code file_size} masih bertipe teks, mengikuti desain. Ini utang
     * teknis yang sudah tercatat: seharusnya BIGINT dalam byte lalu diformat
     * saat ditampilkan, supaya bisa diurutkan dan dijumlahkan.
     */
    private String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.forLanguageTag("id"), "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.forLanguageTag("id"), "%.1f MB", bytes / (1024.0 * 1024));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String bersih = value.trim();
        return bersih.isEmpty() ? null : bersih;
    }
}
