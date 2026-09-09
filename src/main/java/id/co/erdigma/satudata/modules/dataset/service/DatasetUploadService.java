package id.co.erdigma.satudata.modules.dataset.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.AuditAction;
import id.co.erdigma.satudata.exception.BusinessValidationException;
import id.co.erdigma.satudata.modules.audit.service.AuditLogService;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetRequestCreateDTO;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetResponse;
import id.co.erdigma.satudata.modules.dataset.helper.AccessRuleValidator;
import id.co.erdigma.satudata.modules.dataset.helper.RichTextSanitizer;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetCollection;
import id.co.erdigma.satudata.modules.dataset.entity.Topic;
import id.co.erdigma.satudata.modules.dataset.helper.SlugGenerator;
import id.co.erdigma.satudata.modules.dataset.repository.CollectionRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;
import id.co.erdigma.satudata.modules.dataset.repository.TopicRepository;
import id.co.erdigma.satudata.modules.dataset.service.DatasetFileService.UploadedFile;

import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Menerbitkan dataset baru dari berkas yang diunggah.
 *
 * Satu dataset boleh memuat BEBERAPA berkas, sesuai desain "Tambah dataset":
 * tiap berkas punya nama versi manusia dan jenisnya sendiri. SETIAP berkas
 * yang isinya bisa dibaca akan punya tabelnya sendiri; berkas dokumen seperti
 * PDF dan Word tersimpan sebagai pendamping yang bisa diunduh dan dibaca di
 * halaman.
 *
 * Pembagian tugasnya tetap sama: apa yang bisa diverifikasi mesin dihitung
 * sendiri, apa yang butuh pertanggungjawaban manusia diminta dari penerbit.
 *
 * <h2>Pemeriksaan dan penyimpanan berkasnya bukan di sini</h2>
 *
 * Keduanya dipegang {@link DatasetFileService}, dan dipakai bersama dengan
 * penyuntingan dataset. Batas ukuran serta pencocokan jenis dengan ekstensi
 * adalah urusan keamanan, dan menyalinnya ke dua tempat berarti suatu saat
 * hanya satu yang diperbaiki.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatasetUploadService {

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private AccessRuleValidator accessRuleValidator;
    @Autowired
    private RichTextSanitizer richTextSanitizer;
    @Autowired
    private TopicRepository topicRepository;
    @Autowired
    private CollectionRepository collectionRepository;
    @Autowired
    private DatasetFileService datasetFileService;
    @Autowired
    private DatasetService datasetService;
    @Autowired
    private SlugGenerator slugGenerator;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private AuditLogService auditLogService;

    @Transactional
    public DatasetResponse upload(User user, DatasetRequestCreateDTO body,
            List<MultipartFile> files) {
        // Dataset tanpa berkas tidak punya apa pun untuk diunduh maupun
        // ditampilkan, jadi penolakannya di sini dan bukan di pemeriksa
        // bersama: penyuntingan boleh mengirim nol berkas baru, penerbitan
        // tidak.
        boolean anyFile = files != null && files.stream().anyMatch(f -> f != null && !f.isEmpty());
        if (!anyFile) {
            throw new BusinessValidationException("Minimal satu berkas wajib diunggah.");
        }

        List<UploadedFile> uploads = datasetFileService.validate(
                body.getFiles(), files, body.getTitle().trim(), 0, 0L);

        if (user.getDivision() == null) {
            throw new BusinessValidationException(
                    "Akun Anda belum terhubung ke divisi mana pun, sehingga tidak ada yang bisa "
                            + "dicatat sebagai penerbit dataset ini.");
        }

        String slug = resolveSlug(body);
        long totalByte = uploads.stream().mapToLong(b -> b.file().getSize()).sum();

        Dataset dataset = new Dataset();
        dataset.setSlug(slug);
        dataset.setTitle(body.getTitle().trim());
        // Divisi diambil dari akun, bukan dari isian. Penerbit tidak perlu —
        // dan tidak boleh — mengaku mewakili divisi lain.
        dataset.setDivision(user.getDivision());
        // Dicatat sekali saat unggah dan tidak pernah diubah setelahnya: ini
        // jejak siapa yang bertanggung jawab, bukan penanda pemilik saat ini.
        dataset.setUploadedBy(user);
        // Deskripsi ditulis lewat editor teks kaya, jadi isinya HTML. Yang
        // dibersihkan yang masuk, bukan yang keluar: sekali tersimpan kotor, ia
        // akan digambar di halaman setiap orang yang berhak membuka dataset ini.
        dataset.setNotes(trimToNull(richTextSanitizer.sanitize(body.getNotes())));
        dataset.setDisclaimer(trimToNull(body.getDisclaimer()));
        dataset.setCoverage(trimToNull(body.getCoverage()));
        dataset.setCollection(resolveCollection(body.getCollectionSlug()));
        dataset.setTopics(resolveTopics(body.getTopics()));
        dataset.setAccessRules(accessRuleValidator.validate(body.getAccessRules()));
        // Lencana format menyusul berkas yang benar-benar masuk, bukan
        // ditetapkan CSV di depan seperti dulu.
        dataset.setFormats(uploads.stream().map(UploadedFile::format).distinct().toList());
        dataset.setLastUpdatedAt(LocalDateTime.now());
        dataset.setFileSize(datasetFileService.humanSize(totalByte));
        datasetRepository.save(dataset);

        // Dinomori 1..n. Datasetnya baru dibuat, jadi belum ada nama yang bisa
        // ditabrak.
        datasetFileService.store(dataset, uploads, true);

        // Ikut transaksi yang sama dengan penerbitannya. Kalau salah satu berkas
        // gagal dan semuanya dibatalkan, catatan "dataset dibuat" harus ikut
        // batal — audit yang mencatat kejadian yang tidak pernah terjadi lebih
        // buruk daripada tidak ada audit sama sekali.
        auditLogService.recordDataset(user, AuditAction.CREATE, dataset,
                uploads.size() == 1
                        ? "Menerbitkan dataset dari berkas " + uploads.get(0).originalName() + "."
                        : "Menerbitkan dataset dari " + uploads.size() + " berkas.");

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
        // front-end. Tanpa menghitung kunjungan: menerbitkan dataset bukan
        // mengunjunginya, dan penerbitnya tidak seharusnya menaikkan sendiri
        // angka kunjungan datasetnya di detik pertama.
        return datasetService.getBySlug(user, slug, false, null, null);
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
        String requested = trimToNull(body.getSlug());
        if (requested == null) {
            return slugGenerator.uniqueSlug(body.getTitle(), datasetRepository::existsBySlug);
        }
        if (!slugGenerator.isValid(requested)) {
            throw new BusinessValidationException(
                    "Slug \"" + requested + "\" tidak sah. Pakai huruf kecil, angka, dan tanda "
                            + "hubung saja — misalnya penjualan-furnitur-2025.");
        }
        if (datasetRepository.existsBySlug(requested)) {
            throw new BusinessValidationException(
                    "Slug \"" + requested + "\" sudah dipakai. Usulan yang tersedia: "
                            + slugGenerator.uniqueSlug(requested, datasetRepository::existsBySlug));
        }
        return requested;
    }

    private DatasetCollection resolveCollection(String slug) {
        String cleaned = trimToNull(slug);
        if (cleaned == null) {
            return null;
        }
        return collectionRepository.findBySlugAndDeletedAtIsNull(cleaned)
                .orElseThrow(() -> new BusinessValidationException(
                        "Koleksi \"" + cleaned + "\" tidak ada. Lihat GET /api/v1/collections."));
    }

    private List<Topic> resolveTopics(List<String> names) {
        if (names == null || names.isEmpty()) {
            return new ArrayList<>();
        }
        List<Topic> all = topicRepository.findAllByDeletedAtIsNullOrderBySortOrderAsc();
        List<Topic> result = new ArrayList<>();
        for (String n : names) {
            String cleaned = trimToNull(n);
            if (cleaned == null) {
                continue;
            }
            all.stream()
                    .filter(t -> t.getName().equalsIgnoreCase(cleaned))
                    .findFirst()
                    .ifPresentOrElse(result::add, () -> {
                        throw new BusinessValidationException(
                                "Topik \"" + cleaned + "\" tidak ada. Lihat GET /api/v1/topics.");
                    });
        }
        return result;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
