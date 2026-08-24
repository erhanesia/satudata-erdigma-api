package id.co.erdigma.satudata.modules.stats.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                .countByDownloadedAtGreaterThanEqual(
                        LocalDate.now().minusDays(DEFAULT_DAYS - 1L).atStartOfDay()));
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
    @Transactional(readOnly = true)
    public DailyDownloadResponse getDailyDownloads(int days) {
        int rentang = Math.min(Math.max(days, 1), MAX_DAYS);
        LocalDate sampai = LocalDate.now();
        LocalDate dari = sampai.minusDays(rentang - 1L);

        List<DailyDownloadCount> mentah = downloadLogRepository.countPerDay(dari, sampai);

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
