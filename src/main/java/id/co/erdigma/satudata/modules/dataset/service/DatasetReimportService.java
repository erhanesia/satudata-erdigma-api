package id.co.erdigma.satudata.modules.dataset.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.AuditAction;
import id.co.erdigma.satudata.exception.BusinessValidationException;
import id.co.erdigma.satudata.exception.ResourceNotFoundException;
import id.co.erdigma.satudata.modules.audit.service.AuditLogService;
import id.co.erdigma.satudata.modules.dataset.helper.AdminDivisionScope;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetResource;
import id.co.erdigma.satudata.modules.dataset.helper.XlsxToCsv;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetColumnRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetResourceRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRowRepository;
import id.co.erdigma.satudata.service.storage.FileStorage;
import id.co.erdigma.satudata.service.storage.LocalFileStorage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Membaca ulang isi berkas sebuah dataset yang SUDAH tersimpan.
 *
 * <h2>Kenapa ini ada</h2>
 *
 * Kemampuan membaca Excel datang belakangan. Dataset yang sudah terlanjur
 * diunggah sebelum itu punya berkasnya di penyimpanan, tapi tidak punya satu
 * pun baris di {@code dataset_row} — dan satu-satunya jalan keluar tanpa
 * endpoint ini adalah menghapus lalu mengunggah ulang, yang membuang slug,
 * jejak audit, dan riwayat unduhannya.
 *
 * Hal yang sama akan berlaku setiap kali importirnya diperbaiki: penebakan tipe
 * kolom yang lebih baik, pemisah baru yang dikenali, kolom yang dulu salah
 * baca. Tanpa cara membaca ulang, perbaikan importir hanya berlaku untuk
 * dataset yang diunggah setelahnya.
 *
 * <h2>Yang diganti dan yang tidak</h2>
 *
 * Baris dan kolom lama DIHAPUS lalu ditulis ulang dari berkasnya. Metadata
 * dataset — judul, catatan, topik, tag posisi, pengunggah, penghitung unduhan —
 * tidak disentuh sama sekali. Berkasnya sendiri juga tidak: yang dibaca adalah
 * salinan yang sudah ada di penyimpanan, bukan unggahan baru.
 *
 * <h2>Seluruh berkas, bukan yang pertama saja</h2>
 *
 * Sejak changeset 37 setiap berkas punya tabelnya sendiri, jadi pembacaan ulang
 * memproses SEMUA berkas yang bisa dibaca. Ini sekaligus jalan pemulihan untuk
 * dataset yang terlanjur diunggah sebelum itu: berkas keduanya sudah tersimpan
 * tapi belum punya isi, dan satu panggilan ke sini cukup untuk mengisinya.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatasetReimportService {

    private static final Set<String> READABLE_FORMATS = Set.of("CSV", "XLSX");

    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private AdminDivisionScope adminScope;
    @Autowired
    private DatasetResourceRepository datasetResourceRepository;
    @Autowired
    private DatasetRowRepository datasetRowRepository;
    @Autowired
    private DatasetColumnRepository datasetColumnRepository;
    @Autowired
    private DatasetImportService datasetImportService;
    @Autowired
    private AuditLogService auditLogService;
    @Autowired
    private FileStorage fileStorage;
    @Autowired
    private XlsxToCsv xlsxToCsv;

    @Transactional
    public long reimport(User actor, String slug) {
        Dataset dataset = datasetRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Dataset not found: " + slug));

        // Membaca ulang isi berkas menulis ke tabel baris dan kolom dataset,
        // jadi ia pengubahan, bukan pembacaan. Batas divisinya sama dengan
        // menyunting dan menghapus.
        adminScope.assertCanManage(actor, dataset);

        List<DatasetResource> files = datasetResourceRepository
                .findAllByDatasetIdAndDeletedAtIsNullOrderByFormatSortOrderAscFileNameAsc(
                        dataset.getId());

        List<DatasetResource> readable = files.stream()
                .filter(r -> r.getFormat() != null && READABLE_FORMATS.contains(r.getFormat().getName()))
                .filter(r -> !LocalFileStorage.SEED_PROVIDER.equalsIgnoreCase(r.getStorageProvider()))
                .toList();

        if (readable.isEmpty()) {
            throw new BusinessValidationException(
                    "Dataset ini tidak punya berkas CSV maupun Excel yang bisa dibaca "
                            + "menjadi tabel.");
        }

        long rows = 0;
        for (DatasetResource source : readable) {
            rows += reimportOne(dataset, source);
        }

        // Berkas yang mewakili dataset dipilih ulang dari hasil yang baru:
        // pembacaan ulang bisa mengubah jumlah baris, dan dengan itu berubah
        // pula berkas mana yang paling pantas mewakilinya.
        datasetImportService.electMainResource(dataset);

        auditLogService.recordDataset(actor, AuditAction.UPDATE, dataset,
                readable.size() == 1
                        ? "Isi tabel dibaca ulang dari " + readable.get(0).getFileName()
                                + " — " + rows + " baris."
                        : "Isi tabel dibaca ulang dari " + readable.size() + " berkas — "
                                + rows + " baris.");

        log.info("Dataset {} dibaca ulang dari {} berkas: {} baris", slug, readable.size(), rows);
        return rows;
    }

    /**
     * Membaca ulang satu berkas menjadi tabel miliknya sendiri.
     *
     * Isi lamanya dikosongkan lebih dulu, dan pengosongannya per BERKAS. Dulu
     * seluruh isi dataset yang dihapus — benar ketika satu dataset hanya punya
     * satu tabel, tapi sekarang itu berarti berkas yang sudah selesai dibaca
     * ikut terhapus oleh berkas berikutnya.
     */
    private long reimportOne(Dataset dataset, DatasetResource source) {
        // Importir menolak menulis kalau kolomnya sudah ada — perilaku yang
        // benar untuk unggahan baru, tapi di sini justru membuat pembacaan
        // ulang tidak melakukan apa-apa.
        datasetRowRepository.deleteAllByResourceId(source.getId());
        datasetColumnRepository.deleteAll(
                datasetColumnRepository.findAllByResourceIdAndDeletedAtIsNullOrderBySortOrderAsc(
                        source.getId()));

        Path temp = null;
        Path csv = null;
        try {
            String extension = source.getFormat().getName().toLowerCase(Locale.ROOT);
            temp = Files.createTempFile("satudata-reimport-", "." + extension);
            try (InputStream in = fileStorage.open(source.getStorageKey())) {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            }

            Path toRead = temp;
            if ("XLSX".equals(source.getFormat().getName())) {
                csv = xlsxToCsv.convert(temp);
                toRead = csv;
            }

            datasetImportService.importCsv(dataset, toRead,
                    source.getContentType() == null ? "text/csv" : source.getContentType(), source);

        } catch (IOException e) {
            log.error("Gagal membaca ulang {} pada {}", source.getFileName(), dataset.getSlug(), e);
            throw new BusinessValidationException(
                    "Berkas \"" + source.getFileName() + "\" gagal dibaca ulang. "
                            + "Isinya mungkin rusak atau bukan tabel.");
        } finally {
            deleteQuietly(csv);
            deleteQuietly(temp);
        }

        long rows = datasetRowRepository.countByResourceId(source.getId());
        log.info("Berkas {} dibaca ulang: {} baris", source.getFileName(), rows);
        return rows;
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Berkas sementara {} tidak terhapus", path, e);
        }
    }
}
