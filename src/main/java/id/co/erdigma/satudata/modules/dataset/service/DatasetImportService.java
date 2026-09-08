package id.co.erdigma.satudata.modules.dataset.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.exception.BusinessValidationException;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetColumn;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetResource;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetRow;
import id.co.erdigma.satudata.modules.dataset.entity.Format;
import id.co.erdigma.satudata.modules.dataset.helper.ColumnTypeGuesser;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetColumnRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetResourceRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRowRepository;
import id.co.erdigma.satudata.modules.dataset.repository.FormatRepository;
import id.co.erdigma.satudata.service.storage.FileStorage;
import id.co.erdigma.satudata.service.storage.StoredFile;
import id.co.erdigma.satudata.service.storage.StoredFileCleaner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Mengubah satu berkas CSV menjadi isi dataset: skema kolom, baris JSONB, dan
 * satu berkas terdaftar di penyimpanan. Berkas sumber hanya dibaca dan disalin,
 * tidak pernah diubah.
 *
 * Nanti dipakai ulang oleh endpoint unggah pada milestone admin — sumbernya
 * berganti dari Path menjadi MultipartFile, sisanya sama.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatasetImportService {

    private static final int BATCH_SIZE = 1000;

    /**
     * Pemisah kolom yang dikenali, diurut sesuai prioritas bila jumlahnya seri.
     *
     * Sebelumnya hanya koma yang dikenali, dan itu memakan korban: berkas dari
     * Excel dengan pengaturan regional Indonesia memakai titik koma, sehingga
     * SELURUH baris header terbaca sebagai satu nama kolom. Kalau header itu
     * lebih panjang dari 100 karakter, database menolaknya dan penerbit hanya
     * melihat galat 500 tanpa hint. Kalau kebetulan lebih pendek, yang
     * terjadi lebih buruk lagi: dataset terbit dengan satu kolom bernama
     * "tanggal;kota;produk;..." tanpa ada satu pun peringatan.
     */
    private static final char[] DELIMITER_CANDIDATES = { ',', ';', '	', '|' };

    /** Nama tampilan tiap pemisah, untuk pesan galat yang bisa dimengerti. */
    private static final Map<Character, String> DELIMITER_NAMES = Map.of(
            ',', "koma", ';', "titik koma", '	', "tab", '|', "garis tegak");

    /**
     * Batas kolom {@code dataset_column.machine_name} di database (changeset
     * 00007). Diperiksa di sini supaya pelanggarannya menjadi 400 yang
     * menjelaskan, bukan 500 dari Postgres yang tidak berarti apa-apa bagi
     * penerbit.
     */
    private static final int MAX_COLUMN_NAME_LENGTH = 100;

    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private DatasetRowRepository datasetRowRepository;
    @Autowired
    private DatasetColumnRepository datasetColumnRepository;
    @Autowired
    private DatasetResourceRepository datasetResourceRepository;
    @Autowired
    private FormatRepository formatRepository;
    @Autowired
    private FileStorage fileStorage;
    @Autowired
    private StoredFileCleaner storedFileCleaner;
    @Autowired
    private ColumnTypeGuesser columnTypeGuesser;

    /** Label dan tipe tampilan per kolom teknis pada CSV penjualan furnitur. */
    private static final Map<String, String[]> COLUMN_META = new LinkedHashMap<>();
    static {
        COLUMN_META.put("year", new String[] { "Tahun", "Numeric", "" });
        COLUMN_META.put("month", new String[] { "Bulan", "Numeric", "" });
        COLUMN_META.put("day", new String[] { "Tanggal", "Numeric", "" });
        COLUMN_META.put("sales_date_std", new String[] { "Tanggal Penjualan", "Date", "dd/MM/yyyy" });
        COLUMN_META.put("order_id", new String[] { "ID Pesanan", "Text", "" });
        COLUMN_META.put("customer_name", new String[] { "Pelanggan", "Text", "" });
        COLUMN_META.put("product_name", new String[] { "Produk", "Text", "" });
        COLUMN_META.put("category", new String[] { "Kategori", "Text", "" });
        COLUMN_META.put("price", new String[] { "Harga Satuan", "Numeric", "Rp" });
        COLUMN_META.put("quantity", new String[] { "Kuantitas", "Numeric", "unit" });
        COLUMN_META.put("discount", new String[] { "Diskon", "Numeric", "Rp" });
        COLUMN_META.put("total", new String[] { "Subtotal", "Numeric", "Rp" });
        COLUMN_META.put("shipping_fee", new String[] { "Ongkir", "Numeric", "Rp" });
        COLUMN_META.put("total_sales", new String[] { "Total Penjualan", "Numeric", "Rp" });
        COLUMN_META.put("status", new String[] { "Status", "Text", "" });
        COLUMN_META.put("shipping_address", new String[] { "Alamat Kirim", "Text", "" });
        COLUMN_META.put("kota", new String[] { "Kota", "Text", "" });
    }

    /**
     * Bentuk lama: mendaftarkan berkasnya SEKALIGUS membaca isinya. Dipakai
     * importir seed, yang memang hanya berurusan dengan satu berkas.
     *
     * Berkas didaftarkan LEBIH DULU, tidak lagi sesudah isinya dibaca. Sejak
     * changeset 37 setiap baris menyimpan berkas asalnya, dan berkas itu harus
     * sudah punya id sebelum baris pertama ditulis.
     */
    @Transactional
    public void importCsv(Dataset dataset, Path source, String contentType) throws IOException {
        DatasetResource resource = saveResource(dataset, source, contentType);
        importCsv(dataset, source, contentType, resource);
        electMainResource(dataset);
    }

    /**
     * Membaca isi satu berkas menjadi tabel milik berkas itu.
     *
     * @param resource berkas yang sudah terdaftar dan menjadi asal isi ini.
     *                 Baris dan kolomnya ditandai dengannya, sehingga satu
     *                 dataset bisa memuat beberapa tabel tanpa isinya
     *                 bercampur.
     */
    @Transactional
    public void importCsv(Dataset dataset, Path source, String contentType,
            DatasetResource resource) throws IOException {
        if (resource == null) {
            throw new IllegalArgumentException(
                    "Isi tabel harus punya berkas asal - importCsv dipanggil tanpa resource.");
        }
        log.info("Mengimpor {} ke dataset {} (berkas {})", source, dataset.getSlug(),
                resource.getFileName());

        try (BufferedReader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                log.warn("Berkas CSV kosong, impor dibatalkan.");
                return;
            }
            headerLine = stripBom(headerLine);
            char delimiter = detectDelimiter(headerLine);
            List<String> headers = parseCsvLine(headerLine, delimiter);
            headers = dropTrailingEmptyColumn(headers);
            validateHeaders(headers, delimiter);
            log.info("Pemisah kolom terdeteksi: {} ({} kolom)", DELIMITER_NAMES.get(delimiter), headers.size());

            // Contoh nilai per kolom, dikumpulkan sambil mengalirkan baris.
            // Kolom baru bisa disimpan setelah loop karena tipe datanya ditebak
            // dari isinya — dan isinya baru diketahui setelah dibaca.
            List<List<String>> samples = new ArrayList<>();
            for (int i = 0; i < headers.size(); i++) {
                samples.add(new ArrayList<>());
            }

            List<DatasetRow> batch = new ArrayList<>(BATCH_SIZE);
            long rowNumber = 0;
            long total = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = parseCsvLine(line, delimiter);
                Map<String, Object> data = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    String value = i < values.size() ? values.get(i) : null;
                    data.put(headers.get(i), value);
                    if (samples.get(i).size() < ColumnTypeGuesser.SAMPLE_SIZE && value != null) {
                        samples.get(i).add(value);
                    }
                }

                DatasetRow row = new DatasetRow();
                row.setDatasetId(dataset.getId());
                row.setResourceId(resource.getId());
                row.setRowNumber(++rowNumber);
                row.setData(data);
                batch.add(row);

                if (batch.size() >= BATCH_SIZE) {
                    datasetRowRepository.saveAll(batch);
                    total += batch.size();
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                datasetRowRepository.saveAll(batch);
                total += batch.size();
            }

            saveColumns(dataset, resource, headers, samples);

            resource.setRowCount(total);
            resource.setColCount(headers.size());
            datasetResourceRepository.save(resource);

            // Angka milik dataset TIDAK ditetapkan di sini. Berkas ini belum
            // tentu yang mewakili datasetnya, dan itu baru bisa diketahui
            // setelah semua berkasnya selesai dibaca — lihat
            // electMainResource.
            log.info("Impor selesai: {} baris, {} kolom.", total, headers.size());
        }
    }

    /**
     * COLUMN_META tetap dipakai lebih dulu bila kolomnya dikenal, karena label
     * Indonesia dan satuan yang ditulis tangan di sana lebih baik daripada apa
     * pun yang bisa ditebak. Kolom di luar itu ditebak dari isinya.
     */
    private void saveColumns(Dataset dataset, DatasetResource resource,
            List<String> headers, List<List<String>> samples) {
        // Penjagaan "sudah pernah diimpor" kini per BERKAS. Dulu per dataset,
        // dan itu membuat berkas kedua pada dataset yang sama tidak pernah
        // mendapat kolom sama sekali - barisnya masuk, tapi tabelnya tampil
        // tanpa satu pun judul kolom.
        if (datasetColumnRepository.countByResourceIdAndDeletedAtIsNull(resource.getId()) > 0) {
            return;
        }
        List<DatasetColumn> columns = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            String machine = headers.get(i);
            String[] meta = COLUMN_META.get(machine);

            DatasetColumn column = new DatasetColumn();
            column.setDataset(dataset);
            column.setResourceId(resource.getId());
            column.setMachineName(machine);
            if (meta != null) {
                column.setDisplayName(meta[0]);
                column.setDataType(meta[1]);
                column.setUnit(meta[2].isEmpty() ? null : meta[2]);
            } else {
                column.setDisplayName(columnTypeGuesser.prettifyName(machine));
                column.setDataType(columnTypeGuesser.guessType(samples.get(i)));
                column.setUnit(null);
            }
            column.setSortOrder(i + 1);
            columns.add(column);
        }
        datasetColumnRepository.saveAll(columns);
    }

    /**
     * Menentukan berkas mana yang mewakili dataset, lalu menyalin angkanya.
     *
     * Yang dipilih adalah tabel dengan BARIS TERBANYAK, bukan berkas yang
     * kebetulan diunggah paling dulu. Satu angka di halaman katalog harus
     * mewakili isi dataset, dan antara CSV lima baris dengan Excel 61.876
     * baris, yang kedualah yang sebenarnya orang cari. Urutan unggah tidak
     * mengatakan apa-apa tentang itu.
     *
     * Menjumlahkan seluruh berkas bukan pilihan: dua berkas yang isinya sama
     * akan terbaca sebagai dua kali lipat data yang sebenarnya ada.
     *
     * Seri diputus oleh berkas yang lebih dulu terdaftar, supaya hasilnya
     * tetap sama setiap kali dihitung ulang.
     */
    @Transactional
    public void electMainResource(Dataset dataset) {
        List<DatasetResource> files = datasetResourceRepository
                .findAllByDatasetIdAndDeletedAtIsNullOrderByFormatSortOrderAscFileNameAsc(
                        dataset.getId());

        DatasetResource main = files.stream()
                .filter(r -> r.getRowCount() > 0)
                .max(java.util.Comparator.comparingLong(DatasetResource::getRowCount)
                        .thenComparing(DatasetResource::getCreatedAt,
                                java.util.Comparator.reverseOrder()))
                .orElse(null);

        for (DatasetResource r : files) {
            boolean shouldBe = main != null && r.getId().equals(main.getId());
            if (r.isTableSource() != shouldBe) {
                r.setTableSource(shouldBe);
                datasetResourceRepository.save(r);
            }
        }

        dataset.setRowCount(main == null ? 0 : main.getRowCount());
        dataset.setColCount(main == null ? 0 : main.getColCount());
        datasetRepository.save(dataset);

        log.info("Berkas utama dataset {}: {}", dataset.getSlug(),
                main == null ? "tidak ada tabel" : main.getFileName());
    }

    /**
     * Menyimpan satu berkas milik dataset, apa pun jenisnya.
     *
     * Terpisah dari pembacaan isi CSV dengan sengaja: dataset boleh memuat
     * XLSX, PDF, atau DOCX yang tidak punya tabel untuk dibaca, dan berkas
     * seperti itu tetap harus bisa diunduh.
     *
     * Nama berkas dibuat dari slug dataset plus nomor urut, BUKAN dari nama
     * berkas asal. Nama asal datang dari mesin orang lain: bisa memuat spasi,
     * karakter yang tidak sah di sistem berkas lain, atau bahkan pemisah path.
     * Nama aslinya tidak hilang — yang ditulis penerbit tersimpan di kolom
     * label.
     */
    @Transactional
    public DatasetResource registerFile(Dataset dataset, Path source, Format format,
            String label, String contentType, int order) {
        String extension = format.getName().toLowerCase(java.util.Locale.ROOT);
        String fileName = order == 1
                ? dataset.getSlug() + "." + extension
                : dataset.getSlug() + "-" + order + "." + extension;
        String storageKey = "dataset/" + dataset.getSlug() + "/" + fileName;
        StoredFile stored = fileStorage.storeFrom(source, storageKey, contentType);
        // Ditandai SEBELUM barisnya disimpan. Penyimpanan berhasil sementara
        // transaksinya kemudian batal adalah persis keadaan yang meninggalkan
        // berkas yatim di S3.
        storedFileCleaner.deleteOnRollback(stored.getStorageKey());

        DatasetResource resource = new DatasetResource();
        resource.setDataset(dataset);
        resource.setFormat(format);
        resource.setLabel(label);
        resource.setFileName(fileName);
        resource.setContentType(contentType);
        resource.setStorageProvider(stored.getStorageProvider());
        resource.setStorageKey(stored.getStorageKey());
        resource.setSizeBytes(stored.getSizeBytes());
        resource.setChecksumSha256(stored.getChecksumSha256());
        datasetResourceRepository.save(resource);

        log.info("Berkas terdaftar: {} ({} byte)", storageKey, stored.getSizeBytes());
        return resource;
    }

    private DatasetResource saveResource(Dataset dataset, Path source, String contentType) {
        List<DatasetResource> existing = datasetResourceRepository
                .findAllByDatasetIdAndDeletedAtIsNullOrderByFormatSortOrderAscFileNameAsc(dataset.getId());
        if (!existing.isEmpty()) {
            // Berkasnya sudah pernah didaftarkan. Yang pertama dikembalikan,
            // bukan null: isi yang akan dibaca tetap harus punya berkas asal,
            // dan tanpa itu barisnya tersimpan tanpa penunjuk lalu tidak
            // pernah muncul di tab mana pun.
            return existing.get(0);
        }
        Optional<Format> csvFormat = formatRepository.findAllByDeletedAtIsNullOrderBySortOrderAsc().stream()
                .filter(f -> "CSV".equals(f.getName()))
                .findFirst();
        if (csvFormat.isEmpty()) {
            throw new BusinessValidationException(
                    "Format CSV belum terdaftar di tabel format, sehingga berkasnya tidak bisa disimpan.");
        }

        String fileName = dataset.getSlug() + ".csv";
        String storageKey = "dataset/" + dataset.getSlug() + "/" + fileName;
        StoredFile stored = fileStorage.storeFrom(source, storageKey, contentType);
        // Ditandai SEBELUM barisnya disimpan. Penyimpanan berhasil sementara
        // transaksinya kemudian batal adalah persis keadaan yang meninggalkan
        // berkas yatim di S3.
        storedFileCleaner.deleteOnRollback(stored.getStorageKey());

        DatasetResource resource = new DatasetResource();
        resource.setDataset(dataset);
        resource.setFormat(csvFormat.get());
        resource.setFileName(fileName);
        // Jalur seed tidak punya penerbit yang mengisi nama berkas, jadi judul
        // datasetnya dipakai — supaya tidak ada baris yang tampil tanpa nama.
        resource.setLabel(dataset.getTitle());
        resource.setContentType(contentType);
        resource.setStorageProvider(stored.getStorageProvider());
        resource.setStorageKey(stored.getStorageKey());
        resource.setSizeBytes(stored.getSizeBytes());
        resource.setChecksumSha256(stored.getChecksumSha256());
        datasetResourceRepository.save(resource);

        log.info("Berkas terdaftar: {} ({} byte)", storageKey, stored.getSizeBytes());
        return resource;
    }

    /** Pembaca CSV minimal yang menghormati tanda kutip ganda. */
    /**
     * Membuang Byte Order Mark di awal berkas.
     *
     * Excel menulis BOM pada berkas CSV UTF-8, dan {@code BufferedReader} tidak
     * membuangnya. Tanpa ini, kolom pertama diam-diam bernama "﻿Track"
     * alih-alih "Track" — tidak seen mata, tapi setiap pencarian kunci
     * JSONB untuk kolom itu akan gagal tanpa penjelasan.
     */
    private String stripBom(String headerLine) {
        return headerLine.startsWith("﻿") ? headerLine.substring(1) : headerLine;
    }

    /**
     * Membuang satu kolom kosong di ujung header.
     *
     * Banyak alat mengakhiri baris header dengan pemisah, sehingga
     * {@code "a;b;c;"} terbaca sebagai empat kolom dengan yang terakhir kosong.
     * Itu artefak penulisan, bukan kesalahan penerbit, jadi dirapikan diam-diam
     * — sebelumnya seluruh unggahan ditolak karenanya.
     *
     * Hanya kolom paling ujung yang diampuni. Kolom kosong di TENGAH tetap
     * ditolak {@link #validateHeaders}, karena itu menandakan header yang memang
     * rusak dan menerimanya berarti membiarkan satu kolom data kehilangan nama.
     */
    private List<String> dropTrailingEmptyColumn(List<String> headers) {
        if (headers.size() > 1 && headers.get(headers.size() - 1).isBlank()) {
            List<String> cleaned = new ArrayList<>(headers.subList(0, headers.size() - 1));
            log.info("Pemisah berlebih di ujung header diabaikan; kolom menjadi {}.", cleaned.size());
            return cleaned;
        }
        return headers;
    }

    /**
     * Menebak pemisah kolom dari baris header: yang paling banyak muncul di
     * luar tanda kutip, itulah pemisahnya.
     *
     * Cara ini sederhana dan cukup, karena baris header hampir selalu berisi
     * nama-nama kolom pendek tanpa tanda baca. Kutip diperhitungkan supaya
     * judul seperti {@code "Nama, Lengkap";umur} tidak salah dihitung sebagai
     * berpemisah koma.
     *
     * Bila tidak ada satu pun candidate yang ditemukan, koma dipakai sebagai
     * bawaan — hasilnya satu kolom, dan {@link #validateHeaders} yang akan
     * menjelaskannya kepada penerbit bila itu memang keliru.
     */
    private char detectDelimiter(String headerLine) {
        char chosen = DELIMITER_CANDIDATES[0];
        int highest = 0;
        for (char candidate : DELIMITER_CANDIDATES) {
            int count = countOutsideQuotes(headerLine, candidate);
            if (count > highest) {
                highest = count;
                chosen = candidate;
            }
        }
        return chosen;
    }

    private int countOutsideQuotes(String line, char target) {
        int count = 0;
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == target && !inQuotes) {
                count++;
            }
        }
        return count;
    }

    /**
     * Menolak header yang pasti bermasalah, dengan pesan yang menyebut apa yang
     * salah dan apa yang harus diperbaiki.
     *
     * Semua yang diperiksa di sini dulunya lolos sampai ke database dan berubah
     * jadi galat 500 — atau lebih buruk, lolos diam-diam dan menghasilkan
     * dataset yang strukturnya rusak.
     */
    private void validateHeaders(List<String> headers, char delimiter) {
        for (int i = 0; i < headers.size(); i++) {
            String name = headers.get(i);

            if (name.isBlank()) {
                throw new BusinessValidationException(
                        "Nama kolom ke-" + (i + 1) + " dari " + headers.size()
                                + " kosong. Periksa baris pertama berkas: ada dua pemisah "
                                + "berdempetan, atau satu judul kolom belum diisi.");
            }

            if (name.length() > MAX_COLUMN_NAME_LENGTH) {
                String hint = headers.size() == 1
                        ? " Berkas ini terbaca hanya sebagai SATU kolom, jadi besar kemungkinan "
                                + "pemisahnya bukan " + DELIMITER_NAMES.get(delimiter)
                                + ". Simpan ulang sebagai CSV berpemisah koma."
                        : "";
                throw new BusinessValidationException(
                        "Nama kolom ke-" + (i + 1) + " terlalu panjang ("
                                + name.length() + " karakter, maksimal " + MAX_COLUMN_NAME_LENGTH
                                + "): \"" + name.substring(0, 60) + "...\"." + hint);
            }
        }

        // Kunci JSONB tidak boleh kembar: yang belakangan akan menimpa yang
        // duluan, sehingga satu kolom hilang tanpa jejak.
        Set<String> seen = new LinkedHashSet<>();
        for (String name : headers) {
            if (!seen.add(name)) {
                throw new BusinessValidationException(
                        "Nama kolom \"" + name + "\" muncul lebih dari sekali. "
                                + "Setiap kolom harus punya nama yang berbeda.");
            }
        }
    }

    private List<String> parseCsvLine(String line, char delimiter) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == delimiter) {
                out.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        out.add(current.toString().trim());
        return out;
    }
}
