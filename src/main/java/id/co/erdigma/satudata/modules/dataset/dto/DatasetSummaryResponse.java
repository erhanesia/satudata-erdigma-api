package id.co.erdigma.satudata.modules.dataset.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * Bahan grafik di halaman detail. Sengaja generik — pengelompokan dan metriknya
 * ditentukan pemanggil, sehingga endpoint yang sama melayani dataset apa pun,
 * bukan hanya CSV penjualan furnitur.
 */
@Data
public class DatasetSummaryResponse {
    private String slug;
    private String groupBy;
    private String metric;
    private long totalRows;
    private List<SummaryGroup> groups;

    @Data
    public static class SummaryGroup {
        private String label;
        private long count;
        private BigDecimal sum;
    }
}
