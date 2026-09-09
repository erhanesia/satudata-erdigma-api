package id.co.erdigma.satudata.modules.download.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.modules.download.entity.DownloadLog;

/**
 * Menjalankan query penyaring log terhadap Postgres yang sebenarnya.
 *
 * <h2>Kenapa perlu, padahal sudah ada {@link id.co.erdigma.satudata.modules.download.service.DownloadLogAccessFilterTest}</h2>
 *
 * Kelas itu memakai repository tiruan. Ia membuktikan bahwa service meneruskan
 * nilai yang benar, dan berhenti di situ: query-nya sendiri tidak pernah
 * dijalankan satu kali pun.
 *
 * Bentuk {@code (:accessType IS NULL OR l.accessType = :accessType)} justru
 * termasuk yang bisa lolos pemeriksaan saat aplikasi dinyalakan tetapi gagal
 * saat dieksekusi, karena penyedia JPA perlu menyimpulkan tipe sebuah parameter
 * yang kadang bernilai null. Kegagalannya muncul sebagai galat 500 di halaman
 * admin, bukan sebagai tes merah.
 *
 * Proyek ini pernah tertipu persis oleh pola ini sebelumnya: bug penyuntingan
 * berkas yang lolos 26 tes bermock dan baru terlihat saat Hibernate menyimpan.
 *
 * <h2>Kenapa aman dijalankan</h2>
 *
 * {@code @Transactional} pada kelas tes membuat setiap metode digulung balik
 * setelah selesai. Baris yang disisipkan di sini tidak pernah benar-benar
 * menetap di database pengembang.
 */
@SpringBootTest(properties = "satudata.storage.provider=LOCAL")
@Transactional
class DownloadLogSearchPersistenceTest {

    @Autowired
    private DownloadLogRepository downloadLogRepository;

    private static final LocalDateTime AWAL = LocalDate.of(2000, 1, 1).atStartOfDay();

    private static LocalDateTime akhir() {
        return LocalDate.now().plusDays(1).atStartOfDay();
    }

    /**
     * Menyisipkan satu baris yang bisa dikenali kembali.
     *
     * Slug-nya dibuat acak supaya tes ini tidak bergantung pada isi database
     * pengembang, yang berbeda di setiap mesin dan berubah setiap kali seseorang
     * mencoba mengunduh sesuatu.
     */
    private String insertRow(String accessType) {
        return insertRow(accessType, "tes", UUID.randomUUID(), LocalDateTime.now());
    }

    private String insertRow(String accessType, String cognitoId, UUID datasetId,
            LocalDateTime waktu) {
        String slug = "tes-" + UUID.randomUUID();

        DownloadLog row = new DownloadLog();
        row.setCognitoId(cognitoId);
        row.setUserName("Tes Otomatis");
        row.setUserEmail("tes@erdigma.co.id");
        row.setDivisionCode("DIT");
        row.setDatasetId(datasetId);
        row.setDatasetSlug(slug);
        row.setFileName("tes.csv");
        row.setSizeBytes(10);
        row.setAccessType(accessType);
        row.setChannel("WEB");
        row.setAgreementAccepted("DOWNLOAD".equals(accessType));
        row.setDownloadedAt(waktu);

        downloadLogRepository.saveAndFlush(row);
        return slug;
    }

    private boolean contains(Page<DownloadLog> page, String slug) {
        return page.getContent().stream()
                .anyMatch(l -> slug.equals(l.getDatasetSlug()));
    }

    @Test
    @DisplayName("penyaring kosong mengembalikan kedua jenis akses")
    void nullAccessTypeReturnsBoth() {
        String unduhan = insertRow("DOWNLOAD");
        String pratinjau = insertRow("PREVIEW");

        Page<DownloadLog> hasil = downloadLogRepository.search(
                AWAL, akhir(), null, PageRequest.of(0, 500));

        assertThat(contains(hasil, unduhan)).isTrue();
        assertThat(contains(hasil, pratinjau)).isTrue();
    }

    @Test
    @DisplayName("DOWNLOAD menyingkirkan baris pratinjau")
    void downloadExcludesPreview() {
        String unduhan = insertRow("DOWNLOAD");
        String pratinjau = insertRow("PREVIEW");

        Page<DownloadLog> hasil = downloadLogRepository.search(
                AWAL, akhir(), "DOWNLOAD", PageRequest.of(0, 500));

        assertThat(contains(hasil, unduhan)).isTrue();
        assertThat(contains(hasil, pratinjau)).isFalse();
    }

    @Test
    @DisplayName("PREVIEW menyingkirkan baris unduhan")
    void previewExcludesDownload() {
        String unduhan = insertRow("DOWNLOAD");
        String pratinjau = insertRow("PREVIEW");

        Page<DownloadLog> hasil = downloadLogRepository.search(
                AWAL, akhir(), "PREVIEW", PageRequest.of(0, 500));

        assertThat(contains(hasil, pratinjau)).isTrue();
        assertThat(contains(hasil, unduhan)).isFalse();
    }

    /*
      findRecent menopang dua aturan sekaligus: pembatasan pembukaan dataset
      sekali sehari, dan penggabungan unduhan dalam satu aksi. Keduanya
      memutuskan MENULIS ATAU TIDAK berdasarkan jawabannya, jadi query yang
      salah tidak menghasilkan galat melainkan jumlah baris yang salah.
    */
    @Test
    @DisplayName("findRecent menemukan baris milik orang dan dataset yang sama")
    void findRecentMatchesSameActorAndDataset() {
        UUID datasetId = UUID.randomUUID();
        insertRow("PREVIEW", "orang-a", datasetId, LocalDateTime.now());

        assertThat(downloadLogRepository.findRecent("orang-a", datasetId, "PREVIEW",
                LocalDate.now().atStartOfDay(), PageRequest.of(0, 1))).hasSize(1);
    }

    @Test
    @DisplayName("findRecent tidak tertukar antar orang, dataset, maupun jenis akses")
    void findRecentDoesNotLeakAcrossKeys() {
        UUID datasetId = UUID.randomUUID();
        LocalDateTime sekarang = LocalDateTime.now();
        insertRow("PREVIEW", "orang-a", datasetId, sekarang);

        LocalDateTime sejak = LocalDate.now().atStartOfDay();
        // orang lain
        assertThat(downloadLogRepository.findRecent("orang-b", datasetId, "PREVIEW",
                sejak, PageRequest.of(0, 1))).isEmpty();
        // dataset lain
        assertThat(downloadLogRepository.findRecent("orang-a", UUID.randomUUID(), "PREVIEW",
                sejak, PageRequest.of(0, 1))).isEmpty();
        // jenis akses lain
        assertThat(downloadLogRepository.findRecent("orang-a", datasetId, "DOWNLOAD",
                sejak, PageRequest.of(0, 1))).isEmpty();
    }

    @Test
    @DisplayName("findRecent mengabaikan baris yang lebih tua dari batas waktunya")
    void findRecentIgnoresOlderRows() {
        UUID datasetId = UUID.randomUUID();
        insertRow("DOWNLOAD", "orang-a", datasetId, LocalDateTime.now().minusMinutes(10));

        // Jendela penggabungan unduhan hitungan detik: baris sepuluh menit lalu
        // adalah aksi lain, dan harus menghasilkan baris baru.
        assertThat(downloadLogRepository.findRecent("orang-a", datasetId, "DOWNLOAD",
                LocalDateTime.now().minusSeconds(30), PageRequest.of(0, 1))).isEmpty();

        // Tetapi untuk pembatasan harian, baris itu masih terhitung.
        assertThat(downloadLogRepository.findRecent("orang-a", datasetId, "DOWNLOAD",
                LocalDate.now().atStartOfDay(), PageRequest.of(0, 1))).hasSize(1);
    }

    /*
      Batas atasnya dibandingkan dengan "<", bukan "<=".

      Kalau suatu saat ada yang merapikannya jadi BETWEEN, unduhan yang terjadi
      hari ini berhenti muncul saat seseorang menyaring "sampai hari ini", dan
      tidak ada galat apa pun yang menandainya.
    */
    @Test
    @DisplayName("baris hari ini ikut terbawa saat rentangnya berakhir hari ini")
    void includesRowsFromToday() {
        String slug = insertRow("DOWNLOAD");

        Page<DownloadLog> hasil = downloadLogRepository.search(
                LocalDate.now().atStartOfDay(),
                LocalDate.now().plusDays(1).atStartOfDay(),
                null, PageRequest.of(0, 500));

        assertThat(contains(hasil, slug)).isTrue();
    }
}
