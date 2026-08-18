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
import id.co.erdigma.satudata.service.storage.LocalFileStorage;
import id.co.erdigma.satudata.service.storage.StoredFile;

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
     * melihat galat 500 tanpa petunjuk. Kalau kebetulan lebih pendek, yang
     * terjadi lebih buruk lagi: dataset terbit dengan satu kolom bernama
     * "tanggal;kota;produk;..." tanpa ada satu pun peringatan.
     */
    private static final char[] PEMISAH_KANDIDAT = { ',', ';', '	', '|' };

    /** Nama tampilan tiap pemisah, untuk pesan galat yang bisa dimengerti. */
    private static final Map<Character, String> NAMA_PEMISAH = Map.of(
            ',', "koma", ';', "titik koma", '	', "tab", '|', "garis tegak");

    /**
     * Batas kolom {@code dataset_column.machine_name} di database (changeset
     * 00007). Diperiksa di sini supaya pelanggarannya menjadi 400 yang
     * menjelaskan, bukan 500 dari Postgres yang tidak berarti apa-apa bagi
     * penerbit.
     */
    private static final int MAKS_PANJANG_NAMA_KOLOM = 100;

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
    private LocalFileStorage localFileStorage;
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

    @Transactional
    public void importCsv(Dataset dataset, Path source, String contentType) throws IOException {
        log.info("Mengimpor {} ke dataset {}", source, dataset.getSlug());

        try (BufferedReader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                log.warn("Berkas CSV kosong, impor dibatalkan.");
                return;
            }
            headerLine = buangBom(headerLine);
            char pemisah = kenaliPemisah(headerLine);
            List<String> headers = parseCsvLine(headerLine, pemisah);
            headers = buangKolomEkorKosong(headers);
            periksaHeader(headers, pemisah);
            log.info("Pemisah kolom terdeteksi: {} ({} kolom)", NAMA_PEMISAH.get(pemisah), headers.size());

            // Contoh nilai per kolom, dikumpulkan sambil mengalirkan baris.
            // Kolom baru bisa disimpan setelah loop karena tipe datanya ditebak
            // dari isinya — dan isinya baru diketahui setelah dibaca.
            List<List<String>> contoh = new ArrayList<>();
            for (int i = 0; i < headers.size(); i++) {
                contoh.add(new ArrayList<>());
            }

            List<DatasetRow> batch = new ArrayList<>(BATCH_SIZE);
            long rowNumber = 0;
            long total = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = parseCsvLine(line, pemisah);
                Map<String, Object> data = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    String nilai = i < values.size() ? values.get(i) : null;
                    data.put(headers.get(i), nilai);
                    if (contoh.get(i).size() < ColumnTypeGuesser.SAMPLE_SIZE && nilai != null) {
                        contoh.get(i).add(nilai);
                    }
                }

                DatasetRow row = new DatasetRow();
                row.setDatasetId(dataset.getId());
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

            saveColumns(dataset, headers, contoh);
            saveResource(dataset, source, contentType);

            dataset.setRowCount(total);
            dataset.setColCount(headers.size());
            datasetRepository.save(dataset);

            log.info("Impor selesai: {} baris, {} kolom.", total, headers.size());
        }
    }

    /**
     * COLUMN_META tetap dipakai lebih dulu bila kolomnya dikenal, karena label
     * Indonesia dan satuan yang ditulis tangan di sana lebih baik daripada apa
     * pun yang bisa ditebak. Kolom di luar itu ditebak dari isinya.
     */
    private void saveColumns(Dataset dataset, List<String> headers, List<List<String>> contoh) {
        if (datasetColumnRepository.countByDatasetId(dataset.getId()) > 0) {
            return;
        }
        List<DatasetColumn> columns = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            String machine = headers.get(i);
            String[] meta = COLUMN_META.get(machine);

            DatasetColumn column = new DatasetColumn();
            column.setDataset(dataset);
            column.setMachineName(machine);
            if (meta != null) {
                column.setDisplayName(meta[0]);
                column.setDataType(meta[1]);
                column.setUnit(meta[2].isEmpty() ? null : meta[2]);
            } else {
                column.setDisplayName(columnTypeGuesser.prettifyName(machine));
                column.setDataType(columnTypeGuesser.guessType(contoh.get(i)));
                column.setUnit(null);
            }
            column.setSortOrder(i + 1);
            columns.add(column);
        }
        datasetColumnRepository.saveAll(columns);
    }

    private void saveResource(Dataset dataset, Path source, String contentType) {
        if (!datasetResourceRepository.findAllByDatasetIdAndDeletedAtIsNull(dataset.getId()).isEmpty()) {
            return;
        }
        Optional<Format> csvFormat = formatRepository.findAllByDeletedAtIsNullOrderBySortOrderAsc().stream()
                .filter(f -> "CSV".equals(f.getName()))
                .findFirst();
        if (csvFormat.isEmpty()) {
            log.warn("Format CSV tidak ada di tabel format — berkas tidak didaftarkan.");
            return;
        }

        String fileName = dataset.getSlug() + ".csv";
        String storageKey = "dataset/" + dataset.getSlug() + "/" + fileName;
        StoredFile stored = localFileStorage.storeFrom(source, storageKey, contentType);

        DatasetResource resource = new DatasetResource();
        resource.setDataset(dataset);
        resource.setFormat(csvFormat.get());
        resource.setFileName(fileName);
        resource.setContentType(contentType);
        resource.setStorageProvider(stored.getStorageProvider());
        resource.setStorageKey(stored.getStorageKey());
        resource.setSizeBytes(stored.getSizeBytes());
        resource.setChecksumSha256(stored.getChecksumSha256());
        datasetResourceRepository.save(resource);

        log.info("Berkas terdaftar: {} ({} byte)", storageKey, stored.getSizeBytes());
    }

    /** Pembaca CSV minimal yang menghormati tanda kutip ganda. */
    /**
     * Membuang Byte Order Mark di awal berkas.
     *
     * Excel menulis BOM pada berkas CSV UTF-8, dan {@code BufferedReader} tidak
     * membuangnya. Tanpa ini, kolom pertama diam-diam bernama "﻿Track"
     * alih-alih "Track" — tidak terlihat mata, tapi setiap pencarian kunci
     * JSONB untuk kolom itu akan gagal tanpa penjelasan.
     */
    private String buangBom(String headerLine) {
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
     * ditolak {@link #periksaHeader}, karena itu menandakan header yang memang
     * rusak dan menerimanya berarti membiarkan satu kolom data kehilangan nama.
     */
    private List<String> buangKolomEkorKosong(List<String> headers) {
        if (headers.size() > 1 && headers.get(headers.size() - 1).isBlank()) {
            List<String> rapi = new ArrayList<>(headers.subList(0, headers.size() - 1));
            log.info("Pemisah berlebih di ujung header diabaikan; kolom menjadi {}.", rapi.size());
            return rapi;
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
     * Bila tidak ada satu pun kandidat yang ditemukan, koma dipakai sebagai
     * bawaan — hasilnya satu kolom, dan {@link #periksaHeader} yang akan
     * menjelaskannya kepada penerbit bila itu memang keliru.
     */
    private char kenaliPemisah(String headerLine) {
        char terpilih = PEMISAH_KANDIDAT[0];
        int terbanyak = 0;
        for (char kandidat : PEMISAH_KANDIDAT) {
            int jumlah = hitungDiLuarKutip(headerLine, kandidat);
            if (jumlah > terbanyak) {
                terbanyak = jumlah;
                terpilih = kandidat;
            }
        }
        return terpilih;
    }

    private int hitungDiLuarKutip(String line, char target) {
        int jumlah = 0;
        boolean dalamKutip = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                dalamKutip = !dalamKutip;
            } else if (c == target && !dalamKutip) {
                jumlah++;
            }
        }
        return jumlah;
    }

    /**
     * Menolak header yang pasti bermasalah, dengan pesan yang menyebut apa yang
     * salah dan apa yang harus diperbaiki.
     *
     * Semua yang diperiksa di sini dulunya lolos sampai ke database dan berubah
     * jadi galat 500 — atau lebih buruk, lolos diam-diam dan menghasilkan
     * dataset yang strukturnya rusak.
     */
    private void periksaHeader(List<String> headers, char pemisah) {
        for (int i = 0; i < headers.size(); i++) {
            String nama = headers.get(i);

            if (nama.isBlank()) {
                throw new BusinessValidationException(
                        "Nama kolom ke-" + (i + 1) + " dari " + headers.size()
                                + " kosong. Periksa baris pertama berkas: ada dua pemisah "
                                + "berdempetan, atau satu judul kolom belum diisi.");
            }

            if (nama.length() > MAKS_PANJANG_NAMA_KOLOM) {
                String petunjuk = headers.size() == 1
                        ? " Berkas ini terbaca hanya sebagai SATU kolom, jadi besar kemungkinan "
                                + "pemisahnya bukan " + NAMA_PEMISAH.get(pemisah)
                                + ". Simpan ulang sebagai CSV berpemisah koma."
                        : "";
                throw new BusinessValidationException(
                        "Nama kolom ke-" + (i + 1) + " terlalu panjang ("
                                + nama.length() + " karakter, maksimal " + MAKS_PANJANG_NAMA_KOLOM
                                + "): \"" + nama.substring(0, 60) + "...\"." + petunjuk);
            }
        }

        // Kunci JSONB tidak boleh kembar: yang belakangan akan menimpa yang
        // duluan, sehingga satu kolom hilang tanpa jejak.
        Set<String> terlihat = new LinkedHashSet<>();
        for (String nama : headers) {
            if (!terlihat.add(nama)) {
                throw new BusinessValidationException(
                        "Nama kolom \"" + nama + "\" muncul lebih dari sekali. "
                                + "Setiap kolom harus punya nama yang berbeda.");
            }
        }
    }

    private List<String> parseCsvLine(String line, char pemisah) {
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
            } else if (c == pemisah) {
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
