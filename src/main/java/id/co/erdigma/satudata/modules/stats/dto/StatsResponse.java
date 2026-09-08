package id.co.erdigma.satudata.modules.stats.dto;

import lombok.Data;

/**
 * Angka untuk stat cards di beranda.
 *
 * Keenam sel di desain kini punya sumber datanya masing-masing. "Total Views"
 * sempat tidak ada — portal belum mencatat kunjungan sama sekali, dan waktu itu
 * yang ditampilkan adalah {@code totalApiCalls} sebagai gantinya. Sejak
 * changeset 00023 kunjungan halaman detail dataset benar-benar dihitung, jadi
 * selnya memakai angkanya sendiri.
 *
 * {@code totalApiCalls} dipertahankan meski tidak lagi muncul di beranda:
 * datanya nyata ada di database dan tetap berguna bagi dasbor. Perlu diketahui
 * angka itu belum bergerak sendiri — penghitungnya baru menyala setelah jalur
 * autentikasi mesin (API key) ada.
 */
@Data
public class StatsResponse {
    private long totalDataset;
    private long totalFormat;
    private long totalDivision;
    private long totalTopic;
    private long totalCollection;
    private long totalDownloads;
    private long totalApiCalls;
    private long totalViews;
    private long totalDatasetWithFile;

    /**
     * Berapa ORANG yang pernah menerbitkan dataset — bukan berapa dataset yang
     * punya pemilik. Kartu "Kontributor" di dasbor admin.
     */
    private long totalContributor;

    /**
     * Karyawan yang barisnya belum di-soft-delete. BUKAN "yang aktif belakangan
     * ini": portal ini stateless dan tidak menyimpan waktu kunjungan terakhir.
     */
    private long totalActiveUser;

    /** Unduhan 30 hari terakhir, dihitung dari download_log. */
    private long totalDownloads30d;
}
