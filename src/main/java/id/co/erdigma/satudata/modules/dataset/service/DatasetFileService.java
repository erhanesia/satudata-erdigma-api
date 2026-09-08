package id.co.erdigma.satudata.modules.dataset.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import id.co.erdigma.satudata.exception.BusinessValidationException;
import id.co.erdigma.satudata.modules.dataset.dto.FileMetaView;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetResource;
import id.co.erdigma.satudata.modules.dataset.entity.Format;
import id.co.erdigma.satudata.modules.dataset.helper.XlsxToCsv;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetColumnRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetResourceRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRowRepository;
import id.co.erdigma.satudata.modules.dataset.repository.FormatRepository;
import id.co.erdigma.satudata.service.storage.FileStorage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Berkas milik dataset: memeriksa unggahan, menyimpannya, dan melepasnya.
 *
 * <h2>Kenapa terpisah dari penerbitan</h2>
 *
 * Menerbitkan dan menyunting sekarang sama-sama menerima berkas, dan yang
 * paling tidak boleh berbeda di antara keduanya adalah <b>pemeriksaannya</b>:
 * batas ukuran per berkas, batas ukuran seluruhnya, jumlah maksimal, dan
 * pencocokan jenis yang dinyatakan dengan ekstensi yang sungguh dikirim.
 *
 * Semua itu urusan keamanan. Kalau disalin ke dua tempat, suatu saat hanya satu
 * yang diperbaiki, dan yang tertinggal jadi jalan masuk yang tidak pernah
 * dilihat siapa pun -- persis karena ia menyalin kode yang sudah "pernah
 * diperiksa".
 *
 * <h2>Yang tetap berbeda, dan itu disengaja</h2>
 *
 * Penerbitan menomori berkasnya 1..n karena datasetnya baru dan belum punya
 * apa-apa. Penyuntingan tidak bisa begitu: nomor bisa sudah terpakai, termasuk
 * oleh berkas yang sudah dihapus tetapi isinya masih menempati kuncinya di
 * penyimpanan. Karena itu penambahan lewat penyuntingan MELEWATI nama yang
 * sudah ada alih-alih menghitung nomornya, dan itu satu-satunya perbedaan
 * perlakuan di antara keduanya.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatasetFileService {

    /** Sejalan dengan spring.servlet.multipart.max-file-size. */
    public static final long MAX_BYTES = 10L * 1024 * 1024;

    /** Batas seluruh permintaan, sejalan dengan max-request-size. */
    public static final long MAX_TOTAL_BYTES = 40L * 1024 * 1024;

    /**
     * Bukan batas teknis melainkan batas akal sehat. Dataset dengan puluhan
     * berkas pendamping biasanya pertanda yang dimaksud sebenarnya beberapa
     * dataset terpisah.
     */
    public static final int MAX_FILES = 10;

    /**
     * Jenis berkas yang isinya bisa dibaca menjadi tabel.
     *
     * PDF dan DOCX sengaja di luar daftar: keduanya dokumen, bukan tabel.
     * Memaksanya jadi baris dan kolom hanya menghasilkan sesuatu yang terlihat
     * seperti data padahal bukan.
     */
    private static final Set<String> READABLE_FORMATS = Set.of("CSV", "XLSX");

    @Autowired
    private FormatRepository formatRepository;
    @Autowired
    private DatasetImportService datasetImportService;
    @Autowired
    private DatasetResourceRepository datasetResourceRepository;
    @Autowired
    private DatasetRowRepository datasetRowRepository;
    @Autowired
    private DatasetColumnRepository datasetColumnRepository;
    @Autowired
    private FileStorage fileStorage;
    @Autowired
    private XlsxToCsv xlsxToCsv;

    /** Satu berkas unggahan beserta keterangan yang sudah diperiksa. */
    public record UploadedFile(MultipartFile file, Format format, String label, String originalName) {
    }

    /**
     * Memeriksa berkas beserta keterangannya, lalu memasangkan keduanya.
     *
     * Pemasangannya BERDASARKAN URUTAN: bagian multipart ke-n dipasangkan
     * dengan keterangan ke-n. Karena itu jumlahnya harus sama persis, dan itu
     * yang pertama diperiksa -- kalau meleset, setiap berkas mendapat nama dan
     * jenis milik berkas lain tanpa satu pun galat yang muncul.
     *
     * @param meta          keterangan tiap berkas, boleh null kalau tidak ada
     * @param files         bagian multipart, boleh null
     * @param fallbackLabel nama yang dipakai kalau keterangannya tidak menyebut nama
     * @param keptCount     berkas lama yang tetap dipertahankan, ikut dihitung
     *                      terhadap batas jumlah
     * @param keptBytes     ukuran berkas lama yang dipertahankan, ikut dihitung
     *                      terhadap batas ukuran seluruhnya
     */
    public List<UploadedFile> validate(List<? extends FileMetaView> meta, List<MultipartFile> files,
            String fallbackLabel, int keptCount, long keptBytes) {
        List<MultipartFile> content = (files == null) ? List.of()
                : files.stream().filter(f -> f != null && !f.isEmpty()).toList();

        if (keptCount + content.size() > MAX_FILES) {
            throw new BusinessValidationException(
                    "Maksimal " + MAX_FILES + " berkas dalam satu dataset.");
        }

        boolean hasMeta = meta != null && !meta.isEmpty();
        if (hasMeta && meta.size() != content.size()) {
            throw new BusinessValidationException(
                    "Keterangan berkas ada " + meta.size() + " sedangkan berkasnya " + content.size()
                            + ". Jumlah keduanya harus sama karena dipasangkan menurut urutan.");
        }

        long total = keptBytes;
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
            FileMetaView m = hasMeta ? meta.get(i) : null;
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
                    label == null ? fallbackLabel : label, originalName));
        }

        if (total > MAX_TOTAL_BYTES) {
            throw new BusinessValidationException(
                    "Total ukuran seluruh berkas melebihi batas " + humanSize(MAX_TOTAL_BYTES) + ".");
        }
        return result;
    }

    /**
     * Menyimpan seluruh berkas, dan membaca isi SETIAP berkas yang bisa dibaca.
     *
     * URUTANNYA PENTING: berkas didaftarkan sebelum isinya dibaca, karena tiap
     * baris menyimpan id berkas asalnya.
     *
     * Berkas mana yang mewakili dataset ditentukan SETELAH semuanya selesai
     * dibaca, karena yang menentukan adalah jumlah barisnya, dan itu belum
     * diketahui sebelum berkas terakhir masuk.
     *
     * @param numbered {@code true} untuk menomori 1..n seperti pada penerbitan;
     *                 {@code false} untuk memilih nama yang belum terpakai,
     *                 seperti pada penyuntingan
     */
    public void store(Dataset dataset, List<UploadedFile> uploads, boolean numbered) {
        Set<String> taken = numbered ? new HashSet<>() : takenFileNames(dataset);

        for (int i = 0; i < uploads.size(); i++) {
            UploadedFile upload = uploads.get(i);
            String extension = upload.format().getName().toLowerCase(Locale.ROOT);
            String fileName = numbered
                    ? DatasetImportService.fileNameFor(dataset, upload.format(), i + 1)
                    : freeFileName(dataset, upload.format(), taken);

            Path temp = null;
            try {
                temp = Files.createTempFile("satudata-upload-", "." + extension);
                upload.file().transferTo(temp);

                boolean readable = READABLE_FORMATS.contains(upload.format().getName());

                DatasetResource resource = datasetImportService.registerFile(dataset, temp,
                        upload.format(), upload.label(), contentTypeOf(upload), fileName);

                if (readable) {
                    // Excel diubah dulu jadi CSV lalu masuk lewat importir yang
                    // sama. Menulis importir kedua khusus Excel berarti
                    // menduplikasi pengenalan tipe kolom, label Indonesia, dan
                    // penulisan per batch -- dan duplikatnya akan menyimpang
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

    /**
     * Melepas satu berkas dari dataset.
     *
     * <h2>Isi tabelnya ikut dibuang, dan itu bukan pilihan</h2>
     *
     * Baris dan kolom menyimpan id berkas asalnya. Membiarkannya berarti tabel
     * yang masih tampil di Data Explorer padahal berkasnya sudah tidak ada lagi
     * untuk diunduh -- pengunjung membaca angka yang tidak bisa ia telusuri
     * sumbernya.
     *
     * <h2>Berkas di penyimpanan baru dihapus SETELAH transaksinya jadi</h2>
     *
     * Ini kebalikan dari {@code deleteOnRollback} pada unggahan, dan alasannya
     * cermin dari alasan yang sama. Penyimpanan objek tidak ikut transaksi
     * Postgres: kalau berkasnya dihapus sekarang lalu transaksinya batal,
     * barisnya kembali sementara isinya hilang selamanya, dan dataset yang
     * terlihat sehat tidak bisa diunduh sama sekali.
     *
     * Menunda sampai commit membalik risikonya menjadi berkas yatim yang cuma
     * memakan ruang. Di antara dua kegagalan, itu yang bisa diperbaiki
     * belakangan.
     */
    public void remove(DatasetResource resource) {
        datasetRowRepository.deleteAllByResourceId(resource.getId());
        datasetColumnRepository.deleteAll(
                datasetColumnRepository.findAllByResourceIdAndDeletedAtIsNullOrderBySortOrderAsc(
                        resource.getId()));
        datasetResourceRepository.delete(resource);

        deleteAfterCommit(resource.getStorageKey());
    }

    /**
     * Menyegarkan angka yang menyusul isi berkas: lencana jenis, ukuran total,
     * dan waktu perubahan terakhir.
     *
     * Dihitung ulang dari berkas yang benar-benar tersisa, bukan ditambah dan
     * dikurangi seiring jalan. Menghitung ulang selalu benar; menyesuaikan
     * seiring jalan hanya benar selama tidak ada satu jalur pun yang lupa.
     */
    public void refreshAggregates(Dataset dataset) {
        List<DatasetResource> files = datasetResourceRepository
                .findAllByDatasetIdAndDeletedAtIsNullOrderByFormatSortOrderAscFileNameAsc(
                        dataset.getId());

        /*
          Isi koleksinya DIGANTI, bukan koleksinya yang ditukar.

          Hibernate memegang koleksi milik entity terkelola lewat pembungkusnya
          sendiri. Menukar seluruh koleksi dengan daftar baru membuat `save()`
          menempuh jalur merge, dan di sana Hibernate memanggil clear() pada
          daftar yang kita berikan -- yang berakhir UnsupportedOperationException
          kalau daftarnya tidak bisa diubah, seperti hasil Stream.toList().

          Galatnya tidak muncul saat baris ini berjalan melainkan saat menyimpan,
          jadi jejaknya menunjuk ke tempat lain. Idiom clear()+addAll() ini yang
          dipakai DatasetAdminService untuk topik dan aturan akses, dan alasannya
          sama.
        */
        dataset.getFormats().clear();
        dataset.getFormats().addAll(distinctFormats(files));

        dataset.setFileSize(humanSize(files.stream().mapToLong(DatasetResource::getSizeBytes).sum()));
        dataset.setLastUpdatedAt(java.time.LocalDateTime.now());
    }

    /**
     * Jenis berkas yang benar-benar dimiliki dataset ini, tanpa pengulangan.
     *
     * Dibedakan menurut ID, bukan lewat {@code distinct()}.
     *
     * {@code DatasetResource.format} dipetakan LAZY, jadi yang dipegang di sini
     * proxy. Lombok membangkitkan equals dan hashCode yang membaca field secara
     * langsung, dan pada proxy yang belum dibuka seluruh field itu masih null --
     * sehingga SEMUA jenis berkas terlihat sama dan menciut jadi satu. Yang
     * hilang bukan galat melainkan lencana: dataset berisi CSV dan PDF cuma
     * menampilkan salah satunya.
     *
     * {@code getId()} aman dipanggil pada proxy: identifier sudah dipegang tanpa
     * perlu membuka isinya.
     */
    private List<Format> distinctFormats(List<DatasetResource> files) {
        Map<java.util.UUID, Format> unik = new LinkedHashMap<>();
        for (DatasetResource file : files) {
            Format format = file.getFormat();
            if (format != null) {
                unik.putIfAbsent(format.getId(), format);
            }
        }
        return new ArrayList<>(unik.values());
    }

    /**
     * Nama berkas yang sudah dipegang dataset ini, TERMASUK milik berkas yang
     * sudah ditandai terhapus.
     *
     * Baris terhapus tetap dihitung karena isinya masih menempati kuncinya di
     * penyimpanan. Melewatkannya berarti unggahan berikutnya bisa menimpa
     * berkas itu tanpa satu pun galat.
     */
    private Set<String> takenFileNames(Dataset dataset) {
        Set<String> names = new HashSet<>();
        for (DatasetResource r : datasetResourceRepository.findAllByDatasetId(dataset.getId())) {
            names.add(r.getFileName());
        }
        return names;
    }

    /**
     * Nama pertama yang belum ditempati, dengan aturan penamaan yang sama seperti
     * penerbitan.
     *
     * Sengaja tidak private: inilah satu-satunya yang berdiri antara unggahan baru
     * dan menimpa berkas yang sudah ada di penyimpanan, dan kegagalannya tidak
     * menghasilkan galat apa pun -- cuma berkas lama yang diam-diam berubah isi.
     * Perilaku semacam itu harus bisa diuji langsung, bukan lewat lima lapis mock.
     */
    static String freeFileName(Dataset dataset, Format format, Set<String> taken) {
        for (int order = 1;; order++) {
            String candidate = DatasetImportService.fileNameFor(dataset, format, order);
            if (taken.add(candidate)) {
                return candidate;
            }
        }
    }

    /**
     * Menghapus berkas dari penyimpanan begitu transaksinya benar-benar jadi.
     *
     * Kegagalan penghapusan sengaja tidak dilempar. Kita berada setelah commit;
     * melempar dari sini hanya menghasilkan galat pada permintaan yang
     * sebenarnya sudah berhasil, sementara yang tertinggal cuma berkas yatim.
     */
    private void deleteAfterCommit(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            doDelete(storageKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                doDelete(storageKey);
            }
        });
    }

    private void doDelete(String storageKey) {
        try {
            fileStorage.delete(storageKey);
            log.info("Berkas {} dilepas dari penyimpanan.", storageKey);
        } catch (Exception e) {
            log.warn("Berkas {} gagal dihapus dari penyimpanan. Berkas ini jadi yatim.",
                    storageKey, e);
        }
    }

    private String contentTypeOf(UploadedFile upload) {
        String declared = upload.file().getContentType();
        return (declared == null || declared.isBlank()) ? "application/octet-stream" : declared;
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
     * Kolom {@code file_size} masih bertipe teks, mengikuti desain. Ini utang
     * teknis yang sudah tercatat: seharusnya BIGINT dalam byte lalu diformat
     * saat ditampilkan, supaya bisa diurutkan dan dijumlahkan.
     */
    public String humanSize(long bytes) {
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

    /**
     * Dipakai penyuntingan untuk membaca daftar berkas yang masih hidup.
     *
     * Ditaruh di sini, bukan dibiarkan pemanggil memanggil repository sendiri,
     * supaya urutan yang sama dengan Data Explorer selalu ikut terbawa.
     */
    @Transactional(readOnly = true)
    public List<DatasetResource> listLive(Dataset dataset) {
        return datasetResourceRepository
                .findAllByDatasetIdAndDeletedAtIsNullOrderByFormatSortOrderAscFileNameAsc(
                        dataset.getId());
    }
}
