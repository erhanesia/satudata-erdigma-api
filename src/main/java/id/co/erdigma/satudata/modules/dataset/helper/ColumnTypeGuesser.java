package id.co.erdigma.satudata.modules.dataset.helper;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Menebak tipe tampilan sebuah kolom dari isinya, dan merapikan nama mesin
 * menjadi nama tampilan.
 *
 * Diperlukan karena sebelumnya nama dan tipe kolom datang dari peta yang
 * dipatok mati untuk 17 kolom CSV penjualan furnitur. Berkas apa pun di luar itu
 * akan menampilkan nama mesin apa adanya dan menganggap SELURUH kolom bertipe
 * Text — termasuk kolom angka, yang berarti grafik {@code /summary} tidak bisa
 * menjumlahkan apa pun.
 *
 * Tebakannya sengaja konservatif: ragu berarti Text. Salah menebak kolom angka
 * sebagai teks hanya membuat grafik tidak menawarkan kolom itu; salah menebak
 * teks sebagai angka membuat perhitungan diam-diam mengabaikan baris yang tidak
 * terbaca, dan itu menghasilkan angka yang salah tanpa peringatan.
 */
@Component
public class ColumnTypeGuesser {

    /** Cukup banyak untuk yakin, cukup sedikit untuk tidak membaca seluruh berkas. */
    public static final int SAMPLE_SIZE = 200;

    /** Ambang kesepakatan. Di bawah ini dianggap tidak meyakinkan. */
    private static final double THRESHOLD = 0.9;

    public String guessType(List<String> samples) {
        List<String> terisi = samples.stream()
                .filter(v -> v != null && !v.isBlank())
                .toList();
        if (terisi.isEmpty()) {
            return "Text";
        }

        long angka = terisi.stream().filter(this::looksNumeric).count();
        if ((double) angka / terisi.size() >= THRESHOLD) {
            return "Numeric";
        }
        long date = terisi.stream().filter(this::looksDate).count();
        if ((double) date / terisi.size() >= THRESHOLD) {
            return "Date";
        }
        return "Text";
    }

    /**
     * Menerima koma sebagai pemisah desimal, karena berkas dari Excel berlokal
     * Indonesia menulis {@code 0,1} bukan {@code 0.1} — persis seperti kolom
     * diskon pada CSV penjualan furnitur.
     *
     * Pemisah ribuan sengaja TIDAK ditangani. {@code 1.234} bisa berarti seribu
     * dua ratus atau satu koma dua tiga empat, dan menebaknya berisiko mengubah
     * nilai. Kolom seperti itu lebih baik jatuh ke Text dan diperbaiki manusia.
     */
    private boolean looksNumeric(String value) {
        String cleaned = value.trim().replace(',', '.');
        if (cleaned.isEmpty() || cleaned.chars().filter(c -> c == '.').count() > 1) {
            return false;
        }
        try {
            Double.parseDouble(cleaned);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean looksDate(String value) {
        String v = value.trim();
        return v.matches("\\d{4}-\\d{2}-\\d{2}([ T].*)?")
                || v.matches("\\d{1,2}/\\d{1,2}/\\d{4}")
                || v.matches("\\d{1,2}-\\d{1,2}-\\d{4}");
    }

    /**
     * {@code "total_sales"} → {@code "Total Sales"}.
     *
     * Sengaja tidak menerjemahkan ke bahasa Indonesia: menebak terjemahan hanya
     * menghasilkan label yang terdengar aneh dan sulit diperbaiki karena
     * penerbit tidak tahu asalnya. Nama yang dirapikan sudah cukup terbaca, dan
     * penerbit tinggal menyuntingnya bila perlu.
     */
    public String prettifyName(String machineName) {
        if (machineName == null || machineName.isBlank()) {
            return machineName;
        }
        String[] kata = machineName.replaceAll("[_\\-]+", " ").trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String k : kata) {
            if (k.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(k.charAt(0)))
                    .append(k.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }
}
