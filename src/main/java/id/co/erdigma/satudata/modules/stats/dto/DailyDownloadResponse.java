package id.co.erdigma.satudata.modules.stats.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Data;

/**
 * Bahan grafik "Download harian" di dasbor admin.
 *
 * {@code days} berisi SATU baris untuk setiap hari dalam rentang, termasuk hari
 * yang jumlahnya nol. Itu disengaja — grafik garis yang melompati tanggal sepi
 * memberi kesan tren yang tidak pernah terjadi.
 */
@Data
public class DailyDownloadResponse {
    private LocalDate from;
    private LocalDate to;
    private long total;
    private List<Day> days;

    @Data
    public static class Day {
        private LocalDate date;
        private long total;
    }
}
