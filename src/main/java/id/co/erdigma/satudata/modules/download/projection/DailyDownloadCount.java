package id.co.erdigma.satudata.modules.download.projection;

import java.time.LocalDate;

/**
 * Hasil mentah satu baris grafik unduhan harian.
 *
 * Antarmuka, bukan kelas: Spring Data mengisinya langsung dari kolom hasil
 * query native ({@code log_date} dan {@code total}) tanpa perlu konstruktor
 * maupun pemetaan manual.
 */
public interface DailyDownloadCount {
    LocalDate getLogDate();

    long getTotal();
}
