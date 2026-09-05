package id.co.erdigma.satudata.modules.dataset.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetCollection;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetResource;
import id.co.erdigma.satudata.modules.dataset.entity.Format;
import id.co.erdigma.satudata.modules.dataset.entity.Topic;
import id.co.erdigma.satudata.modules.dataset.helper.SlugGenerator;
import id.co.erdigma.satudata.modules.dataset.helper.XlsxToCsv;
import id.co.erdigma.satudata.modules.dataset.repository.CollectionRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;
import id.co.erdigma.satudata.modules.dataset.repository.FormatRepository;
import id.co.erdigma.satudata.modules.dataset.repository.TopicRepository;

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
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatasetUploadService {

    /** Sejalan dengan spring.servlet.multipart.max-file-size. */
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    /** Batas seluruh permintaan, sejalan dengan max-request-size. */
    private static final long MAX_TOTAL_BYTES = 40L * 1024 * 1024;

    /**
     * Jenis berkas yang isinya bisa dibaca menjadi tabel.
     *
     * PDF dan DOCX sengaja di luar daftar: keduanya dokumen, bukan tabel.
     * Memaksanya jadi baris dan kolom hanya menghasilkan sesuatu yang terlihat
     * seperti data padahal bukan.
     */
    private static final Set<String> READABLE_FORMATS = Set.of("CSV", "XLSX");

    /**
     * Bukan batas teknis melainkan batas akal sehat. Dataset dengan puluhan
     * berkas pendamping biasanya pertanda yang dimaksud sebenarnya beberapa
     * dataset terpisah.
     */
    private static final int MAX_FILES = 10;

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private AccessRuleValidator accessRuleValidator;
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
    @Autowired
    private AuditLogService auditLogService;
    @Autowired
    private XlsxToCsv xlsxToCsv;

    @Transactional
    public DatasetResponse upload(User user, DatasetRequestCreateDTO body,
            List<MultipartFile> files) {
        List<UploadedFile> uploads = validateFiles(body, files);

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
        dataset.setNotes(trimToNull(body.getNotes()));
        dataset.setDisclaimer(trimToNull(body.getDisclaimer()));
        dataset.setCoverage(trimToNull(body.getCoverage()));
        dataset.setCollection(resolveCollection(body.getCollectionSlug()));
        dataset.setTopics(resolveTopics(body.getTopics()));
        dataset.setAccessRules(accessRuleValidator.validate(body.getAccessRules()));
        // Lencana format menyusul berkas yang benar-benar masuk, bukan
        // ditetapkan CSV di depan seperti dulu.
        dataset.setFormats(uploads.stream().map(UploadedFile::format).distinct().toList());
        dataset.setLastUpdatedAt(LocalDateTime.now());
        dataset.setFileSize(humanSize(totalByte));
        datasetRepository.save(dataset);

        storeFiles(dataset, uploads);

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
        return datasetService.getBySlug(user, slug, false);
    }

    /** Satu berkas unggahan beserta keterangan yang sudah diperiksa. */
    private record UploadedFile(MultipartFile file, Format format, String label, String originalName) {
    }

    /**
     * Menyimpan seluruh berkas, dan membaca isi SETIAP berkas yang bisa dibaca.
     *
     * Dulu hanya berkas pertama yang dibaca, karena satu dataset hanya punya
     * satu tabel. Batas itu bocor sampai ke muka: dataset berisi CSV dan Excel
     * menampilkan tabel di satu tab dan tulisan "belum bisa ditampilkan" di tab
     * satunya, padahal keduanya spreadsheet biasa. Sejak changeset 37 tabelnya
     * milik berkas, bukan milik dataset, jadi tidak ada lagi alasan menyisakan
     * berkas kedua tanpa isi.
     *
     * Berkas mana yang mewakili dataset baru ditentukan SETELAH semuanya
     * selesai dibaca, karena yang menentukan adalah jumlah barisnya — dan itu
     * belum diketahui sebelum berkas terakhir masuk.
     *
     * URUTANNYA PENTING: berkas didaftarkan sebelum isinya dibaca, karena tiap
     * baris menyimpan id berkas asalnya.
     */
    private void storeFiles(Dataset dataset, List<UploadedFile> uploads) {
        for (int i = 0; i < uploads.size(); i++) {
            UploadedFile upload = uploads.get(i);
            String extension = upload.format().getName().toLowerCase(Locale.ROOT);
            Path temp = null;
            try {
                temp = Files.createTempFile("satudata-upload-", "." + extension);
                upload.file().transferTo(temp);

                boolean readable = READABLE_FORMATS.contains(upload.format().getName());

                DatasetResource resource = datasetImportService.registerFile(dataset, temp,
                        upload.format(), upload.label(), contentTypeOf(upload), i + 1);

                if (readable) {
                    // Excel diubah dulu jadi CSV lalu masuk lewat importir yang
                    // sama. Menulis importir kedua khusus Excel berarti
                    // menduplikasi pengenalan tipe kolom, label Indonesia, dan
                    // penulisan per batch — dan duplikatnya akan menyimpang
                    // diam-diam begitu salah satunya diperbaiki.
                    Path toRead = temp;
                    Path tempCsv = null;
                    try {
                        if ("XLSX".equals(upload.format().getName())) {
                            tempCsv = xlsxToCsv.convert(temp);
                            toRead = tempCsv;
                        }
                        datasetImportService.importCsv(dataset, toRead, contentTypeOf(upload), resource);
                    } finally {
                        if (tempCsv != null) {
                            Files.deleteIfExists(tempCsv);
                        }
                    }
                }

            } catch (IOException e) {
                log.error("Gagal memproses unggahan {} untuk dataset {}",
                        upload.originalName(), dataset.getSlug(), e);
                throw new BusinessValidationException(
                        "Berkas \"" + upload.originalName() + "\" gagal dibaca. Pastikan isinya tidak rusak.");
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

        datasetImportService.electMainResource(dataset);
    }

    private String contentTypeOf(UploadedFile upload) {
        String declared = upload.file().getContentType();
        return (declared == null || declared.isBlank()) ? "application/octet-stream" : declared;
    }

    /**
     * Memeriksa berkas beserta keterangannya, lalu memasangkan keduanya.
     *
     * Pemasangannya BERDASARKAN URUTAN: bagian multipart ke-n dipasangkan
     * dengan keterangan ke-n. Karena itu jumlahnya harus sama persis, dan itu
     * yang pertama diperiksa — kalau meleset, setiap berkas mendapat nama dan
     * jenis milik berkas lain tanpa satu pun galat yang muncul.
     */
    private List<UploadedFile> validateFiles(DatasetRequestCreateDTO body, List<MultipartFile> files) {
        List<MultipartFile> content = (files == null) ? List.of()
                : files.stream().filter(f -> f != null && !f.isEmpty()).toList();

        if (content.isEmpty()) {
            throw new BusinessValidationException("Minimal satu berkas wajib diunggah.");
        }
        if (content.size() > MAX_FILES) {
            throw new BusinessValidationException(
                    "Maksimal " + MAX_FILES + " berkas dalam satu dataset.");
        }

        List<DatasetRequestCreateDTO.FileMeta> meta = body.getFiles();
        boolean hasMeta = meta != null && !meta.isEmpty();
        if (hasMeta && meta.size() != content.size()) {
            throw new BusinessValidationException(
                    "Keterangan berkas ada " + meta.size() + " sedangkan berkasnya " + content.size()
                            + ". Jumlah keduanya harus sama karena dipasangkan menurut urutan.");
        }

        long total = 0;
        List<UploadedFile> result = new ArrayList<>();
        for (int i = 0; i < content.size(); i++) {
            MultipartFile file = content.get(i);
            String originalName = (file.getOriginalFilename() == null
                    || file.getOriginalFilename().isBlank())
                            ? "berkas-" + (i + 1)
                            : file.getOriginalFilename();

            if (file.getSize() > MAX_BYTES) {
                throw new BusinessValidationException(
                        "Ukuran \"" + originalName + "\" melebihi batas " + humanSize(MAX_BYTES) + ".");
            }
            total += file.getSize();

            String extension = extensionOf(originalName);
            DatasetRequestCreateDTO.FileMeta m = hasMeta ? meta.get(i) : null;
            String requested = (m == null) ? null : trimToNull(m.getFormat());

            Format format = resolveFormat(requested == null ? extension : requested, originalName);

            // Jenis yang dipilih penerbit diperiksa terhadap berkas yang sungguh
            // dikirim. Lencana "PDF" pada berkas yang isinya CSV adalah
            // keterangan salah, dan keterangan salah di katalog data lebih
            // berbahaya daripada penolakan.
            if (requested != null && !format.getName().equalsIgnoreCase(extension)) {
                throw new BusinessValidationException(
                        "Jenis berkas \"" + originalName + "\" dipilih " + format.getName()
                                + ", tetapi berkasnya berekstensi ." + extension + ".");
            }

            String label = (m == null) ? null : trimToNull(m.getLabel());
            result.add(new UploadedFile(file, format,
                    label == null ? body.getTitle().trim() : label, originalName));
        }

        if (total > MAX_TOTAL_BYTES) {
            throw new BusinessValidationException(
                    "Total ukuran seluruh berkas melebihi batas " + humanSize(MAX_TOTAL_BYTES) + ".");
        }
        return result;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private Format resolveFormat(String name, String fileName) {
        String wanted = (name == null) ? "" : name.trim();
        return formatRepository.findAllByDeletedAtIsNullOrderBySortOrderAsc().stream()
                .filter(f -> f.getName().equalsIgnoreCase(wanted))
                .findFirst()
                .orElseThrow(() -> new BusinessValidationException(
                        "Jenis berkas \"" + fileName + "\" tidak didukung. Lihat "
                                + "GET /api/v1/formats untuk daftar yang berlaku."));
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

    /**
     * Label posisi divalidasi terhadap daftar yang dikenal, bukan disimpan apa
     * adanya. Salah ketik yang lolos menghasilkan tag yang tidak pernah cocok
     * dengan posisi siapa pun, sehingga datasetnya terkunci dari semua orang
     * kecuali ADMIN dan pengunggahnya — tanpa satu pun galat yang menunjukkan
     * sebabnya.
     */

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
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
