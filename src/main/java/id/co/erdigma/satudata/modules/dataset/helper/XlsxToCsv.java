package id.co.erdigma.satudata.modules.dataset.helper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Mengubah lembar pertama sebuah berkas Excel menjadi CSV.
 *
 * <h2>Kenapa lewat CSV, bukan langsung ke database</h2>
 *
 * Seluruh kecerdasan importir — pengenalan pemisah, penebakan tipe kolom dari
 * isinya, label Indonesia untuk kolom yang dikenal, penulisan per batch — sudah
 * ada dan sudah teruji di jalur CSV. Menulis jalur kedua yang membaca Excel
 * langsung berarti menduplikasi semuanya, dan duplikat itu akan menyimpang
 * diam-diam begitu salah satunya diperbaiki.
 *
 * Harganya satu berkas sementara. Itu murah dibanding dua importir yang
 * lama-lama berbeda perilaku.
 *
 * <h2>Batas yang disengaja</h2>
 *
 * <ul>
 *   <li><b>Hanya lembar pertama.</b> Satu dataset punya satu tabel; lembar
 *       kedua tidak punya tempat untuk ditampilkan, dan menggabungkannya ke
 *       lembar pertama menghasilkan campuran yang tidak berarti apa-apa.</li>
 *   <li><b>Nilai hasil rumus dibaca, bukan rumusnya.</b> Yang dilihat orang di
 *       Excel adalah hasilnya.</li>
 *   <li><b>Berkas dimuat penuh ke memori.</b> Aman karena unggahan dibatasi
 *       10 MB per berkas; XLSX yang mengembang ~10x masih muat. Kalau batas itu
 *       dinaikkan, jalur ini harus pindah ke pembaca SAX
 *       ({@code XSSFReader}) sebelum berkas besar pertama masuk.</li>
 * </ul>
 */
@Component
@Slf4j
public class XlsxToCsv {

    /** Tanggal ditulis apa adanya dalam ISO, bukan format lokal Excel. */
    private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * @return path berkas CSV sementara. Pemanggil yang bertanggung jawab
     *         menghapusnya.
     */
    public Path convert(Path source) throws IOException {
        Path target = Files.createTempFile("satudata-xlsx-", ".csv");

        try (InputStream in = Files.newInputStream(source);
                Workbook workbook = WorkbookFactory.create(in);
                BufferedWriter out = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {

            if (workbook.getNumberOfSheets() == 0) {
                log.warn("Berkas Excel {} tidak punya lembar sama sekali.", source);
                return target;
            }

            Sheet sheet = workbook.getSheetAt(0);
            int width = columnCount(sheet);
            if (width == 0) {
                log.warn("Lembar pertama {} kosong.", source);
                return target;
            }

            int written = 0;
            for (Row row : sheet) {
                // Baris yang seluruh selnya kosong dilewati. Excel gemar
                // menyisakan baris hampa di bawah data, dan tanpa penyaringan
                // ini tabelnya berakhir dengan puluhan baris kosong.
                List<String> value = new ArrayList<>(width);
                boolean hasContent = false;
                for (int i = 0; i < width; i++) {
                    String text = readCell(row.getCell(i));
                    if (!text.isEmpty()) {
                        hasContent = true;
                    }
                    value.add(text);
                }
                if (!hasContent) {
                    continue;
                }

                out.write(String.join(",", value.stream().map(XlsxToCsv::quote).toList()));
                out.newLine();
                written++;
            }

            log.info("Excel {} diubah jadi CSV: {} baris, {} kolom", source, written, width);
            return target;

        } catch (IOException e) {
            Files.deleteIfExists(target);
            throw e;
        } catch (RuntimeException e) {
            Files.deleteIfExists(target);
            // POI melempar bermacam RuntimeException untuk berkas rusak atau
            // berformat lain yang menyamar sebagai .xlsx. Dibungkus jadi
            // IOException supaya pemanggil cukup menangani satu jenis.
            throw new IOException("Berkas Excel tidak bisa dibaca: " + e.getMessage(), e);
        }
    }

    /**
     * Lebar tabel diambil dari BARIS PERTAMA, bukan dari baris terlebar.
     *
     * Baris pertama adalah header, dan header itulah yang menentukan ada berapa
     * kolom. Memakai baris terlebar membuat satu sel nyasar jauh di bawah — hal
     * yang sangat lazim di berkas Excel buatan tangan — menambah kolom hantu
     * tanpa nama ke seluruh tabel.
     */
    private int columnCount(Sheet sheet) {
        Row header = sheet.getRow(sheet.getFirstRowNum());
        if (header == null) {
            return 0;
        }
        int width = header.getLastCellNum();
        // Buang kolom ekor yang headernya kosong.
        while (width > 0 && readCell(header.getCell(width - 1)).isEmpty()) {
            width--;
        }
        return width;
    }

    private String readCell(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType cellType = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType()
                : cell.getCellType();

        return switch (cellType) {
            case STRING -> cell.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case NUMERIC -> readNumber(cell);
            default -> "";
        };
    }

    /**
     * Angka ditulis tanpa notasi ilmiah dan tanpa ekor desimal palsu.
     *
     * Excel menyimpan semua angka sebagai double, sehingga 333 terbaca 333.0 dan
     * bilangan besar bisa keluar sebagai 3.33E8. Keduanya akan membuat penebak
     * tipe kolom di importir menyerah dan menandai kolomnya sebagai Text —
     * grafik lalu tidak menawarkan kolom itu sama sekali.
     */
    private String readNumber(Cell cell) {
        if (DateUtil.isCellDateFormatted(cell)) {
            var dateTime = cell.getLocalDateTimeCellValue();
            if (dateTime == null) {
                return "";
            }
            boolean midnight = dateTime.getHour() == 0 && dateTime.getMinute() == 0
                    && dateTime.getSecond() == 0;
            return midnight ? dateTime.toLocalDate().format(DATE_ONLY) : dateTime.format(DATE_TIME);
        }
        return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
    }

    /** Mengutip hanya bila perlu, supaya CSV-nya tetap enak dibaca manusia. */
    private static String quote(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
