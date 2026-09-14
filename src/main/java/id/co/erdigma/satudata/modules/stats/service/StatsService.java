package id.co.erdigma.satudata.modules.stats.service;

import java.util.UUID;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.modules.dataset.helper.AdminDivisionScope;
import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.modules.division.repository.DivisionRepository;
import id.co.erdigma.satudata.modules.dataset.repository.CollectionRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;
import id.co.erdigma.satudata.modules.dataset.repository.FormatRepository;
import id.co.erdigma.satudata.modules.dataset.repository.TopicRepository;
import id.co.erdigma.satudata.modules.download.projection.DailyDownloadCount;
import id.co.erdigma.satudata.modules.download.repository.DownloadLogRepository;
import id.co.erdigma.satudata.modules.stats.dto.DailyDownloadResponse;
import id.co.erdigma.satudata.modules.stats.dto.StatsResponse;
import id.co.erdigma.satudata.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StatsService {
    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private AdminDivisionScope adminScope;
    @Autowired
    private DivisionRepository divisionRepository;
    @Autowired
    private TopicRepository topicRepository;
    @Autowired
    private FormatRepository formatRepository;
    @Autowired
    private CollectionRepository collectionRepository;
    @Autowired
    private DownloadLogRepository downloadLogRepository;
    @Autowired
    private UserRepository userRepository;

    /** Rentang bawaan grafik dasbor, sesuai judul "Download harian · 30 hari". */
    private static final int DEFAULT_DAYS = 30;

    /** Batas atas yang wajar untuk satu grafik; menahan permintaan `days=100000`. */
    private static final int MAX_DAYS = 365;

    @Transactional(readOnly = true)
    public StatsResponse getStats() {
        StatsResponse response = new StatsResponse();
        response.setTotalDataset(datasetRepository.countByDeletedAtIsNull());
        response.setTotalDivision(divisionRepository.countByDeletedAtIsNull());
        response.setTotalTopic(topicRepository.countByDeletedAtIsNull());
        response.setTotalFormat(formatRepository.countByDeletedAtIsNull());
        response.setTotalCollection(collectionRepository.countByDeletedAtIsNull());
        response.setTotalDownloads(datasetRepository.sumDownloads());
        response.setTotalApiCalls(datasetRepository.sumApiCalls());
        response.setTotalViews(datasetRepository.sumViews());
        response.setTotalDatasetWithFile(datasetRepository.countWithResource());
        response.setTotalContributor(datasetRepository.countContributor());
        response.setTotalActiveUser(userRepository.countByDeletedAtIsNull());
        response.setTotalDownloads30d(downloadLogRepository
                .countDownloadsSince(
                        LocalDate.now().minusDays(DEFAULT_DAYS - 1L).atStartOfDay()));
        return response;
    }

    /**
     * Angka dasbor PANEL ADMIN, dibatasi divisi si admin.
     *
     * <h2>Kenapa metode dan endpoint tersendiri</h2>
     *
     * {@code getStats()} melayani beranda portal, yang dilihat seluruh karyawan
     * dan harus meringkas seluruh katalog. Menyaringnya menurut divisi
     * pemanggil akan membuat angka di beranda menyusut berbeda-beda bagi tiap
     * orang yang membukanya, yaitu perubahan pada halaman yang justru tidak
     * boleh berubah.
     *
     * <h2>Yang TIDAK disaring, dan kenapa</h2>
     *
     * Jumlah topik, format, dan divisi tetap global. Ketiganya data acuan
     * bersama, bukan cerminan cakupan si admin: banyaknya team di Erdigma tetap
     * sama siapa pun yang bertanya. Menyaringnya menghasilkan kartu bertuliskan
     * "Total divisi: 1" yang terbaca seperti kerusakan, bukan seperti
     * keterangan.
     */
    @Transactional(readOnly = true)
    public StatsResponse getStatsForAdmin(User admin) {
        UUID divisionId = adminScope.filterDivisionId(admin);
        if (divisionId == null) {
            // Admin HRIS berurusan dengan seluruh divisi, jadi angkanya sama
            // persis dengan yang global.
            return getStats();
        }

        StatsResponse response = new StatsResponse();
        response.setTotalDataset(datasetRepository.countByDeletedAtIsNullAndDivisionId(divisionId));
        response.setTotalDownloads(datasetRepository.sumDownloadsByDivision(divisionId));
        response.setTotalContributor(datasetRepository.countContributorByDivision(divisionId));
        response.setTotalActiveUser(userRepository.countByDeletedAtIsNullAndDivisionId(divisionId));
        response.setTotalDownloads30d(downloadLogRepository.countDownloadsSinceForDivision(
                LocalDate.now().minusDays(DEFAULT_DAYS - 1L).atStartOfDay(), divisionId));
        response.setTotalApiCalls(datasetRepository.sumApiCallsByDivision(divisionId));
        response.setTotalViews(datasetRepository.sumViewsByDivision(divisionId));
        response.setTotalDatasetWithFile(
                datasetRepository.countWithResourceByDivision(divisionId));

        /*
          Data acuan, sama bagi siapa pun.

          Jumlah topik, format, dan divisi bukan cerminan cakupan si admin:
          banyaknya team di Erdigma tetap sama siapa pun yang bertanya.

          Koleksi ikut di sini karena ia memang TIDAK punya divisi sama
          sekali; tidak ada kolom yang bisa dipakai menyaringnya, dan
          memaksakannya berarti mengarang hubungan yang tidak ada.
        */
        response.setTotalTopic(topicRepository.countByDeletedAtIsNull());
        response.setTotalFormat(formatRepository.countByDeletedAtIsNull());
        response.setTotalDivision(divisionRepository.countByDeletedAtIsNull());
        response.setTotalCollection(collectionRepository.countByDeletedAtIsNull());

        /*
          SELURUH ruas StatsResponse kini terisi, dan itu disengaja.

          Ruas yang dibiarkan kosong tidak menjadi "tidak tahu" melainkan 0,
          karena tipenya primitif. Nol yang berarti "belum diisi" tidak bisa
          dibedakan dari nol yang berarti "memang belum ada", dan pembacanya
          menyimpulkan katalognya kosong padahal cuma jalur ini yang lupa
          mengisinya.

          Kalau kelak ada ruas baru di StatsResponse, ia harus ikut diisi di
          sini juga.
        */
        return response;
    }

    /**
     * Unduhan per hari untuk grafik dasbor.
     *
     * Rentangnya berakhir HARI INI dan mundur {@code days} hari, jadi hari yang
     * sedang berjalan ikut terhitung meski belum selesai. Baris terakhir grafik
     * karena itu wajar terlihat lebih rendah — itu hari yang belum penuh, bukan
     * penurunan.
     */
    /**
     * Grafik unduhan harian untuk PANEL ADMIN, dibatasi divisi si admin.
     *
     * Alasannya sama dengan {@link #getStatsForAdmin(User)}: grafik yang sama
     * juga dipakai di luar panel admin, jadi penyaringannya tidak boleh
     * dipasang pada jalur yang lama.
     */
    @Transactional(readOnly = true)
    public DailyDownloadResponse getDailyDownloadsForAdmin(User admin, int days) {
        return buildDailyDownloads(days, adminScope.filterDivisionId(admin));
    }

    @Transactional(readOnly = true)
    public DailyDownloadResponse getDailyDownloads(int days) {
        return buildDailyDownloads(days, null);
    }

    private DailyDownloadResponse buildDailyDownloads(int days, UUID divisionId) {
        int rentang = Math.min(Math.max(days, 1), MAX_DAYS);
        LocalDate sampai = LocalDate.now();
        LocalDate dari = sampai.minusDays(rentang - 1L);

        List<DailyDownloadCount> mentah = downloadLogRepository.countPerDay(dari, sampai, divisionId);

        List<DailyDownloadResponse.Day> hari = new ArrayList<>(mentah.size());
        long total = 0;
        for (DailyDownloadCount rows : mentah) {
            DailyDownloadResponse.Day item = new DailyDownloadResponse.Day();
            item.setDate(rows.getLogDate());
            item.setTotal(rows.getTotal());
            hari.add(item);
            total += rows.getTotal();
        }

        DailyDownloadResponse response = new DailyDownloadResponse();
        response.setFrom(dari);
        response.setTo(sampai);
        response.setTotal(total);
        response.setDays(hari);
        return response;
    }
}
