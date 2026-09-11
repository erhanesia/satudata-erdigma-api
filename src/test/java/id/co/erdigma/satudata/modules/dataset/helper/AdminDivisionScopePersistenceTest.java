package id.co.erdigma.satudata.modules.dataset.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.AuditAction;
import id.co.erdigma.satudata.enums.HrisPermissionLevel;
import id.co.erdigma.satudata.modules.audit.entity.AuditLog;
import id.co.erdigma.satudata.modules.audit.repository.AuditLogRepository;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;
import id.co.erdigma.satudata.modules.division.entity.Division;
import id.co.erdigma.satudata.modules.division.repository.DivisionRepository;
import id.co.erdigma.satudata.modules.download.entity.DownloadLog;
import id.co.erdigma.satudata.modules.download.repository.DownloadLogRepository;
import id.co.erdigma.satudata.modules.download.projection.DailyDownloadCount;
import id.co.erdigma.satudata.modules.stats.service.StatsService;

/**
 * Menjalankan SELURUH penyaring divisi terhadap Postgres yang sebenarnya.
 *
 * <h2>Kenapa kelas ini ada</h2>
 *
 * {@link AdminDivisionScopeTest} membuktikan bahwa aturannya menghitung divisi
 * yang benar, dan berhenti di situ. Ia tidak pernah menjalankan satu pun query
 * yang memakai angka itu.
 *
 * Sembilan query berbatas divisi ditambahkan bersama fitur ini, dan sebelum
 * kelas ini ada, tidak satu pun tes yang pernah mengirim {@code divisionId}
 * selain null. Artinya seluruh cabang penyaringnya tidak pernah dieksekusi:
 * query yang keliru akan lolos seluruh tes yang ada, lalu berjalan pertama kali
 * di panel admin produksi.
 *
 * <h2>Kenapa kegagalannya berbahaya</h2>
 *
 * Penyaring yang salah TIDAK melempar galat. Ia mengembalikan tabel yang
 * terlihat wajar, terisi, dan berurutan. Yang keliru cuma isinya, yaitu baris
 * milik divisi lain yang seharusnya tidak terlihat, dan tidak ada satu tanda pun
 * di layar. Justru itulah yang seluruh fitur ini berusaha cegah.
 *
 * <h2>Dua yang paling perlu dijaga</h2>
 *
 * <ul>
 *   <li>{@code countPerDay} memakai SQL NATIVE. JPQL diperiksa Hibernate saat
 *       aplikasi menyala, sehingga salah ketik menggagalkan seluruh tes
 *       {@code @SpringBootTest} dengan keras. SQL native tidak: ia baru gagal
 *       saat benar-benar dijalankan, dan satu-satunya yang menjalankannya adalah
 *       grafik dasbor.</li>
 *   <li>{@code searchForAdmin} punya percabangan sungguhan, bukan sekadar
 *       penyaring: jejak dataset dicocokkan lewat slug, jejak selain dataset
 *       lewat kode divisi pelakunya. Dua cabang berarti dua cara untuk salah.</li>
 * </ul>
 *
 * <h2>Kenapa aman dijalankan</h2>
 *
 * {@code @Transactional} menggulung balik setiap metode, jadi baris yang
 * disisipkan tidak pernah menetap di database pengembang.
 *
 * <h2>Kenapa selisih, bukan angka mutlak</h2>
 *
 * Database pengembang sudah berisi data, dan isinya berbeda di tiap mesin.
 * Pemeriksaan yang menuntut angka tertentu akan hijau di satu laptop dan merah
 * di laptop sebelah. Yang diperiksa di sini selalu PERUBAHAN yang disebabkan
 * baris yang baru disisipkan, atau kehadiran slug acak yang pasti milik tes ini
 * sendiri.
 */
@SpringBootTest(properties = "satudata.storage.provider=LOCAL")
@Transactional
class AdminDivisionScopePersistenceTest {

    @Autowired
    private DivisionRepository divisionRepository;
    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private DownloadLogRepository downloadLogRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private StatsService statsService;

    /** Dua divisi yang berbeda: tanpa yang kedua, "tersaring" tidak bisa dibuktikan. */
    private Division divisiA;
    private Division divisiB;

    private Dataset datasetA;
    private Dataset datasetB;

    private static final LocalDateTime AWAL = LocalDate.of(2000, 1, 1).atStartOfDay();

    private static LocalDateTime akhir() {
        return LocalDate.now().plusDays(1).atStartOfDay();
    }

    @BeforeEach
    void seed() {
        List<Division> semua = divisionRepository.findAll();
        assertThat(semua.size())
                .as("tes ini menuntut minimal dua divisi terdaftar")
                .isGreaterThanOrEqualTo(2);

        divisiA = semua.get(0);
        divisiB = semua.get(1);

        datasetA = dataset(divisiA);
        datasetB = dataset(divisiB);
    }

    private Dataset dataset(Division division) {
        Dataset d = new Dataset();
        d.setSlug("uji-cakupan-" + UUID.randomUUID());
        d.setTitle("Uji Cakupan Divisi");
        d.setDivision(division);
        d.setDownloads(7);
        return datasetRepository.saveAndFlush(d);
    }

    private void logAkses(Dataset dataset, String accessType, LocalDateTime waktu) {
        DownloadLog row = new DownloadLog();
        row.setCognitoId("tes");
        row.setUserName("Tes Otomatis");
        row.setUserEmail("tes@erdigma.co.id");
        row.setDivisionCode("DIT");
        row.setDatasetId(dataset.getId());
        row.setDatasetSlug(dataset.getSlug());
        row.setFileName("tes.csv");
        row.setSizeBytes(10);
        row.setAccessType(accessType);
        row.setChannel("WEB");
        row.setAgreementAccepted("DOWNLOAD".equals(accessType));
        row.setDownloadedAt(waktu);
        downloadLogRepository.saveAndFlush(row);
    }

    private void jejak(String objectType, String objectSlug, String actorDivisionCode) {
        AuditLog a = new AuditLog();
        a.setActorCognitoId("tes");
        a.setActorName("Tes Otomatis");
        a.setActorDivisionCode(actorDivisionCode);
        a.setAction(AuditAction.UPDATE);
        a.setObjectType(objectType);
        a.setObjectSlug(objectSlug);
        a.setObjectLabel("Uji");
        a.setRecordedAt(LocalDateTime.now());
        auditLogRepository.saveAndFlush(a);
    }

    private static boolean memuat(Page<DownloadLog> halaman, String slug) {
        return halaman.getContent().stream().anyMatch(l -> slug.equals(l.getDatasetSlug()));
    }

    private static boolean memuatJejak(Page<AuditLog> halaman, String slug) {
        return halaman.getContent().stream().anyMatch(a -> slug.equals(a.getObjectSlug()));
    }

    // ------------------------------------------------------------- log akses

    /*
      Inti seluruh fitur ini, dinyatakan sebagai satu pemeriksaan.

      Kalau baris ini gagal, artinya admin satu divisi sedang membaca siapa saja
      yang mengakses data divisi lain, lengkap dengan nama, surel, dan alamat IP.
    */
    @Test
    @DisplayName("log akses hanya memuat dataset divisi si admin")
    void accessLogIsScopedToOwnDivision() {
        logAkses(datasetA, "DOWNLOAD", LocalDateTime.now());
        logAkses(datasetB, "DOWNLOAD", LocalDateTime.now());

        Page<DownloadLog> hasil = downloadLogRepository.search(
                AWAL, akhir(), null, divisiA.getId(), PageRequest.of(0, 500));

        assertThat(memuat(hasil, datasetA.getSlug())).isTrue();
        assertThat(memuat(hasil, datasetB.getSlug())).isFalse();
    }

    /*
      Penyaring divisi dan penyaring jenis akses harus berlaku BERSAMAAN.

      Bentuk `(:x IS NULL OR ...)` yang ditumpuk gampang menghasilkan OR yang
      salah kurung, dan akibatnya justru melonggarkan: satu syarat terpenuhi
      sudah cukup meloloskan barisnya.
    */
    @Test
    @DisplayName("penyaring divisi dan jenis akses berlaku bersamaan, bukan salah satu")
    void divisionAndAccessTypeApplyTogether() {
        logAkses(datasetA, "PREVIEW", LocalDateTime.now());
        logAkses(datasetB, "DOWNLOAD", LocalDateTime.now());

        Page<DownloadLog> hasil = downloadLogRepository.search(
                AWAL, akhir(), "DOWNLOAD", divisiA.getId(), PageRequest.of(0, 500));

        // Milik A tetapi jenisnya salah, dan milik B yang jenisnya benar tetapi
        // divisinya salah. Keduanya harus tersingkir.
        assertThat(memuat(hasil, datasetA.getSlug())).isFalse();
        assertThat(memuat(hasil, datasetB.getSlug())).isFalse();
    }

    @Test
    @DisplayName("tanpa divisi, admin HRIS melihat kedua divisi")
    void nullDivisionSeesEverything() {
        logAkses(datasetA, "DOWNLOAD", LocalDateTime.now());
        logAkses(datasetB, "DOWNLOAD", LocalDateTime.now());

        Page<DownloadLog> hasil = downloadLogRepository.search(
                AWAL, akhir(), null, null, PageRequest.of(0, 500));

        assertThat(memuat(hasil, datasetA.getSlug())).isTrue();
        assertThat(memuat(hasil, datasetB.getSlug())).isTrue();
    }

    @Test
    @DisplayName("hitungan unduhan 30 hari ikut dibatasi divisi")
    void downloadCountIsScoped() {
        LocalDateTime sejak = LocalDate.now().minusDays(29).atStartOfDay();
        long sebelumA = downloadLogRepository.countDownloadsSinceForDivision(sejak, divisiA.getId());
        long sebelumB = downloadLogRepository.countDownloadsSinceForDivision(sejak, divisiB.getId());

        logAkses(datasetA, "DOWNLOAD", LocalDateTime.now());
        logAkses(datasetA, "PREVIEW", LocalDateTime.now());

        assertThat(downloadLogRepository.countDownloadsSinceForDivision(sejak, divisiA.getId()))
                .as("satu unduhan masuk, pratinjau tidak ikut dihitung")
                .isEqualTo(sebelumA + 1);
        assertThat(downloadLogRepository.countDownloadsSinceForDivision(sejak, divisiB.getId()))
                .as("divisi lain tidak boleh ikut bertambah")
                .isEqualTo(sebelumB);
    }

    // --------------------------------------------------- grafik harian (native)

    /*
      Satu-satunya query SQL NATIVE pada fitur ini, dan karena itu yang paling
      perlu dijalankan sungguhan.

      JPQL diperiksa saat aplikasi menyala; SQL native tidak. `CAST(:divisionId
      AS uuid)` di dalamnya adalah bentuk yang hanya bisa dibuktikan benar dengan
      mengeksekusinya terhadap Postgres.
    */
    @Test
    @DisplayName("grafik unduhan harian ikut dibatasi divisi")
    void dailyChartIsScoped() {
        LocalDate hariIni = LocalDate.now();
        long sebelumA = totalHariIni(divisiA.getId());
        long sebelumB = totalHariIni(divisiB.getId());

        logAkses(datasetA, "DOWNLOAD", LocalDateTime.now());

        assertThat(totalHariIni(divisiA.getId())).isEqualTo(sebelumA + 1);
        assertThat(totalHariIni(divisiB.getId())).isEqualTo(sebelumB);

        // Hari yang kosong tetap muncul bernilai nol, supaya grafiknya tidak
        // berlubang. Diperiksa di sini karena penyaring divisi dipasang di dalam
        // klausa ON, justru supaya sifat itu tidak hilang.
        List<DailyDownloadCount> tujuhHari = downloadLogRepository.countPerDay(
                hariIni.minusDays(6), hariIni, divisiA.getId());
        assertThat(tujuhHari).hasSize(7);
    }

    private long totalHariIni(UUID divisionId) {
        LocalDate hariIni = LocalDate.now();
        return downloadLogRepository.countPerDay(hariIni, hariIni, divisionId).stream()
                .mapToLong(DailyDownloadCount::getTotal)
                .sum();
    }

    // ----------------------------------------------------------- jejak audit

    /*
      Cabang pertama: jejak DATASET dicocokkan lewat slug.

      Dipilih menurut divisi datasetnya, bukan divisi pelakunya, karena yang
      ditanyakan admin adalah "apa yang terjadi pada data saya", bukan "apa saja
      yang dikerjakan orang divisi saya".
    */
    @Test
    @DisplayName("jejak dataset disaring menurut divisi datasetnya")
    void auditDatasetTrailIsScopedByDatasetDivision() {
        jejak("dataset", datasetA.getSlug(), "XXX");
        jejak("dataset", datasetB.getSlug(), "XXX");

        Page<AuditLog> hasil = auditLogRepository.searchForAdmin(
                divisiA.getId(), divisiA.getCode(), null, PageRequest.of(0, 500));

        assertThat(memuatJejak(hasil, datasetA.getSlug())).isTrue();
        assertThat(memuatJejak(hasil, datasetB.getSlug())).isFalse();
    }

    /*
      Cabang kedua: jejak SELAIN dataset dicocokkan lewat kode divisi pelakunya.

      Tidak ada dataset yang bisa dirujuk pada jejak seperti penyuntingan
      pengguna, jadi satu-satunya penanda yang tersisa adalah siapa pelakunya.
    */
    @Test
    @DisplayName("jejak selain dataset disaring menurut kode divisi pelakunya")
    void auditNonDatasetTrailIsScopedByActor() {
        String slugA = "pengguna-" + UUID.randomUUID();
        String slugB = "pengguna-" + UUID.randomUUID();
        jejak("user", slugA, divisiA.getCode());
        jejak("user", slugB, divisiB.getCode());

        Page<AuditLog> hasil = auditLogRepository.searchForAdmin(
                divisiA.getId(), divisiA.getCode(), null, PageRequest.of(0, 500));

        assertThat(memuatJejak(hasil, slugA)).isTrue();
        assertThat(memuatJejak(hasil, slugB)).isFalse();
    }

    /*
      Penyaring slug menumpang di ATAS penyaring divisi, bukan menggantikannya.

      Slug datang dari pemanggil. Kalau ia menjadi jalur tersendiri, admin mana
      pun bisa membaca jejak dataset divisi lain hanya dengan menyebut slug-nya,
      dan penyaringan divisinya jadi hiasan.
    */
    @Test
    @DisplayName("menyebut slug dataset divisi lain tetap tidak menghasilkan apa pun")
    void slugFilterCannotEscapeTheDivision() {
        jejak("dataset", datasetB.getSlug(), "XXX");

        Page<AuditLog> hasil = auditLogRepository.searchForAdmin(
                divisiA.getId(), divisiA.getCode(), datasetB.getSlug(), PageRequest.of(0, 500));

        assertThat(hasil.getTotalElements()).isZero();
    }

    // ---------------------------------------------------------- angka dasbor

    /*
      Dipanggil lewat SERVICE, bukan lewat query satu per satu.

      Bukan kemalasan, melainkan justru yang menangkap lebih banyak. Kartu
      dasbor memakai lima query sekaligus, dan memeriksanya satu per satu
      berarti query keenam yang kelak ditambahkan tidak akan ikut teruji.
      Lewat service, apa pun yang dipakai kartu itu ikut dijalankan.

      Pelajaran ini mahal: versi pertama fitur ini punya satu query yang
      LOLOS startup tetapi gagal saat dijalankan, karena Spring Data menyusun
      teks JPQL-nya di awal sementara Hibernate baru menguraikannya saat
      dipakai. Satu-satunya yang menjalankannya adalah kartu ini, jadi
      kegagalannya muncul pertama kali di hadapan pengguna sebagai 400.
    */
    @Test
    @DisplayName("seluruh kartu dasbor admin benar-benar bisa dihitung")
    void adminDashboardActuallyRuns() {
        User admin = new User();
        admin.setHrisPermissionLevel(HrisPermissionLevel.DIRECTOR);
        admin.setDivision(divisiA);

        // Yang dijaga di sini bukan angkanya, melainkan bahwa kelimanya
        // benar-benar berjalan. Query yang tidak bisa diurai gagal di sini,
        // bukan di dasbor produksi.
        assertThat(statsService.getStatsForAdmin(admin)).isNotNull();
        assertThat(statsService.getDailyDownloadsForAdmin(admin, 30)).isNotNull();
    }

    @Test
    @DisplayName("jumlah dataset dan unduhan pada dasbor ikut dibatasi divisi")
    void dashboardCountsAreScoped() {
        long jumlahSebelum = datasetRepository.countByDeletedAtIsNullAndDivisionId(divisiA.getId());
        long unduhanSebelum = datasetRepository.sumDownloadsByDivision(divisiA.getId());
        long jumlahLainSebelum = datasetRepository.countByDeletedAtIsNullAndDivisionId(divisiB.getId());

        dataset(divisiA);

        assertThat(datasetRepository.countByDeletedAtIsNullAndDivisionId(divisiA.getId()))
                .isEqualTo(jumlahSebelum + 1);
        assertThat(datasetRepository.sumDownloadsByDivision(divisiA.getId()))
                .as("penghitung unduhan dataset barunya ikut terjumlah")
                .isEqualTo(unduhanSebelum + 7);
        assertThat(datasetRepository.countByDeletedAtIsNullAndDivisionId(divisiB.getId()))
                .as("divisi lain tidak boleh ikut bertambah")
                .isEqualTo(jumlahLainSebelum);
    }
}
