package id.co.erdigma.satudata.modules.download.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.exception.BusinessValidationException;
import id.co.erdigma.satudata.exception.ResourceNotFoundException;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetResource;
import id.co.erdigma.satudata.modules.dataset.helper.DatasetAccessGuard;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetResourceRepository;
import id.co.erdigma.satudata.modules.download.dto.DocumentTextResponse;
import id.co.erdigma.satudata.modules.download.dto.DownloadPayload;
import id.co.erdigma.satudata.service.storage.FileStorage;
import id.co.erdigma.satudata.service.storage.LocalFileStorage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Menampilkan isi berkas di halaman, tanpa mengunduhnya.
 *
 * <h2>Kenapa ini tetap dicatat</h2>
 *
 * Pratinjau tidak melewati modal persetujuan, tapi byte-nya tetap keluar dari
 * server dan orangnya tetap bisa membaca seluruh isinya. Berkas rahasia yang
 * bisa dibaca utuh tanpa meninggalkan jejak membuat seluruh guna
 * {@code download_log} hilang — karena itu setiap pratinjau menulis barisnya
 * sendiri, ditandai {@code access_type = PREVIEW} supaya tidak tertukar dengan
 * unduhan yang memang disetujui.
 *
 * <h2>Batas tiap jenis berkas</h2>
 *
 * <ul>
 *   <li><b>PDF</b> dialirkan apa adanya dan digambar peramban sendiri. Tampilan
 *       persis seperti aslinya.</li>
 *   <li><b>DOCX</b> ikut dialirkan apa adanya. Peramban memang tidak bisa
 *       menggambarnya sendiri, tetapi front-end kini menguraikannya di sisi klien
 *       sehingga tabel, gambar, dan tata letaknya ikut tampil. Yang dibutuhkan
 *       dari sini cuma byte-nya.</li>
 *   <li><b>Teks paragraf DOCX</b> tetap tersedia lewat {@code previewText}. Ia
 *       tidak lagi dipakai layar pratinjau, tetapi tetap berguna bagi pemanggil
 *       yang cuma butuh isinya sebagai teks — dan mencabutnya berarti memutus
 *       endpoint publik demi perubahan yang tidak menuntutnya.</li>
 *   <li><b>CSV dan XLSX</b> tidak lewat sini sama sekali. Isinya sudah menjadi
 *       tabel dataset, dan tabel itu jauh lebih berguna daripada teks
 *       mentahnya.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PreviewService {

    /** Batas jumlah paragraf yang dikirim, supaya satu respons tidak jadi berkas sendiri. */
    private static final int MAX_PARAGRAPHS = 2000;

    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private DatasetResourceRepository datasetResourceRepository;
    @Autowired
    private AccessLogService accessLogService;
    @Autowired
    private DatasetAccessGuard accessGuard;
    @Autowired
    private FileStorage fileStorage;

    /**
     * Berkas yang byte-nya dialirkan utuh untuk digambar di halaman.
     *
     * Daftarnya SENGAJA tertutup, bukan "apa saja yang bukan tabel". Endpoint ini
     * mengeluarkan isi berkas tanpa melewati modal persetujuan, jadi setiap jenis
     * yang ditambahkan ke sini adalah keputusan tersendiri yang harus dinyatakan
     * — bukan sesuatu yang ikut terbuka karena kebetulan lolos saringan.
     */
    private static final Set<String> STREAMABLE_FORMATS = Set.of("PDF", "DOCX");

    /**
     * Berkas yang dialirkan apa adanya untuk digambar di halaman.
     *
     * DOCX ikut sejak front-end menguraikannya sendiri di peramban. Sebelumnya
     * yang dikirim cuma teks paragrafnya, dan tabel serta gambar hilang — pada
     * dokumen yang justru isinya tabel, yang tampil bukan ringkasan melainkan
     * potongan yang menyesatkan.
     */
    @Transactional
    public DownloadPayload preview(User user, String slug, UUID resourceId,
            String ipAddress, String userAgent) {
        DatasetResource resource = findFile(user, slug, resourceId);

        if (!STREAMABLE_FORMATS.contains(formatName(resource).toUpperCase(Locale.ROOT))) {
            throw new BusinessValidationException(
                    "Berkas " + formatName(resource) + " tidak bisa ditampilkan di halaman. "
                            + "Yang bisa hanya " + String.join(" dan ", STREAMABLE_FORMATS) + ".");
        }

        recordAccess(user, resource, ipAddress, userAgent);
        return new DownloadPayload(resource, fileStorage.open(resource.getStorageKey()));
    }

    /** Dokumen yang harus diurai lebih dulu — untuk sekarang hanya DOCX. */
    @Transactional
    public DocumentTextResponse previewText(User user, String slug, UUID resourceId,
            String ipAddress, String userAgent) {
        DatasetResource resource = findFile(user, slug, resourceId);

        if (!"DOCX".equalsIgnoreCase(formatName(resource))) {
            throw new BusinessValidationException(
                    "Pratinjau teks hanya untuk dokumen Word. Berkas ini berjenis "
                            + formatName(resource) + ".");
        }

        recordAccess(user, resource, ipAddress, userAgent);

        List<String> paragraphs = new ArrayList<>();
        boolean truncated = false;

        try (InputStream in = fileStorage.open(resource.getStorageKey());
                XWPFDocument document = new XWPFDocument(in)) {

            for (XWPFParagraph p : document.getParagraphs()) {
                String text = p.getText();
                if (text == null || text.isBlank()) {
                    continue;
                }
                if (paragraphs.size() >= MAX_PARAGRAPHS) {
                    truncated = true;
                    break;
                }
                paragraphs.add(text.trim());
            }
        } catch (IOException | RuntimeException e) {
            log.error("Gagal membaca dokumen Word {}", resource.getStorageKey(), e);
            throw new BusinessValidationException(
                    "Dokumen Word gagal dibaca. Berkasnya mungkin rusak atau bukan .docx.");
        }

        DocumentTextResponse response = new DocumentTextResponse();
        response.setFileName(resource.getFileName());
        response.setLabel(resource.getLabel());
        response.setParagraphs(paragraphs);
        response.setTruncated(truncated);
        return response;
    }

    /**
     * Mengambil berkas sekaligus memeriksa haknya.
     *
     * Berkas HARUS milik dataset yang disebut di URL. Tanpa pemeriksaan ini,
     * siapa pun bisa menyebut slug dataset yang boleh ia buka lalu menempelkan
     * id berkas milik dataset lain yang tertutup untuknya.
     */
    private DatasetResource findFile(User user, String slug, UUID resourceId) {
        Dataset dataset = datasetRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Dataset not found: " + slug));
        accessGuard.assertCanView(user, dataset);

        DatasetResource resource = (resourceId == null)
                ? datasetResourceRepository
                        .findFirstByDatasetIdAndDeletedAtIsNullOrderByCreatedAtAsc(dataset.getId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Dataset " + slug + " belum memiliki berkas."))
                : datasetResourceRepository.findById(resourceId)
                        .filter(r -> r.getDeletedAt() == null)
                        .filter(r -> r.getDataset().getId().equals(dataset.getId()))
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Berkas tidak ditemukan pada dataset " + slug + "."));

        if (LocalFileStorage.SEED_PROVIDER.equalsIgnoreCase(resource.getStorageProvider())) {
            throw new BusinessValidationException(
                    "\"" + dataset.getTitle() + "\" adalah dataset contoh. Keterangan berkasnya "
                            + "ada, tetapi isinya memang tidak disertakan dalam data dummy.");
        }
        return resource;
    }

    /**
     * Mencatat bahwa datasetnya dibuka, BUKAN bahwa berkas ini diambil.
     *
     * Dulu tiap pengambilan berkas menulis barisnya sendiri, sehingga membuka
     * satu dataset berisi PDF tercatat sedangkan membuka dataset berisi CSV
     * tidak, padahal tindakan penggunanya sama. Yang membedakan cuma pipa yang
     * kebetulan dipakai penggambarnya, dan itu detail teknis yang bocor menjadi
     * kebijakan audit.
     *
     * Sekarang seluruh jalur menuju satu tempat, dan pembatasan harian di sana
     * membuat pemanggilan berulang tidak berakibat apa-apa.
     */
    private void recordAccess(User user, DatasetResource resource, String ipAddress,
            String userAgent) {
        accessLogService.recordOpen(user, resource.getDataset(), ipAddress, userAgent);
    }

    private String formatName(DatasetResource resource) {
        return resource.getFormat() == null ? "" : resource.getFormat().getName();
    }
}
