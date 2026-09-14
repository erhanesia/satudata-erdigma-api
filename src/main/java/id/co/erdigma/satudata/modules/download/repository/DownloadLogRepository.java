package id.co.erdigma.satudata.modules.download.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import id.co.erdigma.satudata.modules.download.entity.DownloadLog;
import id.co.erdigma.satudata.modules.download.projection.DailyDownloadCount;

@Repository
public interface DownloadLogRepository extends JpaRepository<DownloadLog, Long> {

    Page<DownloadLog> findAllByDatasetIdOrderByDownloadedAtDesc(UUID datasetId, Pageable pageable);

    Page<DownloadLog> findAllByCognitoIdOrderByDownloadedAtDesc(String cognitoId, Pageable pageable);

    /**
     * Baris milik satu aksi unduh, bila sudah ada.
     *
     * Penandanya datang dari pemanggil, jadi pencariannya DIBATASI pada orang
     * dan dataset yang sama juga. Tanpa itu, penanda yang ditebak atau dipakai
     * ulang bisa menempelkan unduhan seseorang ke baris milik orang lain.
     */
    Optional<DownloadLog> findFirstByActionIdAndCognitoIdAndDatasetId(
            UUID actionId, String cognitoId, UUID datasetId);

    /**
     * Peristiwa terakhir orang ini pada dataset ini sejak waktu tertentu.
     *
     * <h2>Satu method untuk dua aturan yang kelihatannya berbeda</h2>
     *
     * Pembatasan pembukaan dataset dan penggabungan unduhan sama-sama bertanya
     * hal yang sama: <b>adakah peristiwa sejenis dari orang yang sama pada
     * dataset yang sama, belum lama ini.</b> Yang membedakan hanya arti "belum
     * lama": sejak awal hari untuk yang pertama, sejak beberapa detik lalu
     * untuk yang kedua.
     *
     * Karena itu batas waktunya diserahkan ke pemanggil, dan aturan yang
     * ketiga nanti tidak perlu menambah method lagi.
     *
     * <h2>Kenapa mengembalikan List, bukan Optional</h2>
     *
     * Pemanggilnya menyertakan {@code PageRequest.of(0, 1)}, jadi isinya nol
     * atau satu. Bentuk ini dipilih karena batas baris lewat Pageable berlaku
     * di semua versi Spring Data, sedangkan LIMIT di dalam JPQL bergantung
     * pada versi penyedia JPA-nya.
     */
    @Query("""
            SELECT l FROM DownloadLog l
            WHERE l.cognitoId = :cognitoId
              AND l.datasetId = :datasetId
              AND l.accessType = :accessType
              AND l.downloadedAt >= :since
            ORDER BY l.downloadedAt DESC
            """)
    List<DownloadLog> findRecent(@Param("cognitoId") String cognitoId,
            @Param("datasetId") UUID datasetId,
            @Param("accessType") String accessType,
            @Param("since") LocalDateTime since,
            Pageable pageable);

    /**
     * Satu pintu untuk halaman Log dan untuk ekspor CSV-nya.
     *
     * <h2>Kenapa satu query, bukan beberapa nama method</h2>
     *
     * Sebelumnya penyaringnya dua method terpisah, satu untuk "tanpa tanggal"
     * dan satu untuk "dalam rentang". Dua keadaan, dua method. Menambah satu
     * penyaring lagi membuatnya jadi empat, penyaring berikutnya delapan, dan
     * nama method-nya memanjang mengikuti setiap kombinasi.
     *
     * Dengan parameter yang boleh kosong, keadaan sebanyak apa pun tetap satu
     * query, dan penyaring berikutnya cukup menambah satu baris di WHERE.
     *
     * <h2>Rentangnya selalu ada, meski penanggalannya tidak diisi</h2>
     *
     * Pemanggilnya yang mengisi batas terbuka dengan nilai selebar-lebarnya,
     * jadi di sini tidak perlu ada cabang "kalau tanggalnya kosong". Yang
     * benar-benar boleh kosong hanya {@code accessType}, dan kosong di situ
     * berarti "semua jenis akses", bukan "tidak ada satu pun".
     */
    /*
      Penyaring divisi lewat subquery, bukan join.

      `download_log` menyimpan `dataset_id` sebagai kolom biasa, bukan relasi
      yang dipetakan, dan itu memang disengaja: baris log harus tetap utuh
      walau datasetnya kelak dihapus. Konsekuensinya di sini, menyaring
      menurut divisi datasetnya harus lewat subquery.

      Yang dibandingkan divisi DATASET-nya, bukan divisi pengunduhnya.
      Kolom `division_code` pada baris log menerangkan siapa yang mengunduh,
      dan memakainya akan menjawab pertanyaan yang lain sama sekali: bukan
      "siapa mengakses data divisi saya", melainkan "orang divisi saya
      mengakses apa saja".
    */
    @Query("""
            SELECT l FROM DownloadLog l
            WHERE l.downloadedAt >= :start
              AND l.downloadedAt < :end
              AND (:accessType IS NULL OR l.accessType = :accessType)
              AND (:divisionId IS NULL OR l.datasetId IN (
                    SELECT d.id FROM Dataset d WHERE d.division.id = :divisionId))
            ORDER BY l.downloadedAt DESC
            """)
    Page<DownloadLog> search(@Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("accessType") String accessType,
            @Param("divisionId") UUID divisionId,
            Pageable pageable);

    /**
     * Isi kartu "Download 30 hari" di dasbor admin.
     *
     * Baris {@code PREVIEW} SENGAJA tidak ikut. Tabel ini mencatat dua peristiwa
     * berbeda: unduhan yang melewati modal persetujuan, dan pratinjau yang tidak.
     * Keduanya sama-sama mengeluarkan byte dari server sehingga sama-sama perlu
     * tercatat, tetapi hanya yang pertama berarti "diunduh".
     *
     * Alasannya sama persis dengan yang tertulis di
     * {@code DivisionRepository.findAllWithDownloads()}. Tanpa penyaringan ini,
     * dua tempat di aplikasi yang sama menjawab pertanyaan yang sama dengan angka
     * yang berbeda — dan dasbor admin akan menyebut bilangan yang lebih besar
     * daripada yang dilaporkan halaman divisi.
     *
     * JANGAN dikembalikan ke nama turunan Spring Data. Nama seperti
     * {@code countByDownloadedAtGreaterThanEqual} menyatakan bahwa query-nya
     * hanya membandingkan waktu, dan itu berhenti benar begitu ada penyaringan
     * jenis akses. Nama yang berbohong lebih berbahaya daripada nama yang panjang.
     */
    @Query("""
            SELECT COUNT(l) FROM DownloadLog l
             WHERE l.downloadedAt >= :sejak
               AND l.accessType = 'DOWNLOAD'
            """)
    long countDownloadsSince(@Param("sejak") LocalDateTime sejak);

    /**
     * Kembaran divisi dari {@link #countDownloadsSince(LocalDateTime)}.
     *
     * Dibatasi divisi DATASET-nya, bukan divisi pengunduhnya, alasannya sama
     * dengan pada {@code search}: yang ditanyakan "seberapa sering data divisi
     * saya diambil", bukan "orang divisi saya mengambil apa saja".
     */
    @Query("""
            SELECT COUNT(l) FROM DownloadLog l
             WHERE l.downloadedAt >= :sejak
               AND l.accessType = 'DOWNLOAD'
               AND l.datasetId IN (
                    SELECT d.id FROM Dataset d WHERE d.division.id = :divisionId)
            """)
    long countDownloadsSinceForDivision(@Param("sejak") LocalDateTime sejak,
            @Param("divisionId") UUID divisionId);

    /**
     * Jumlah unduhan per hari untuk grafik dasbor.
     *
     * Query native, dan {@code generate_series} di dalamnya bukan gaya-gayaan:
     * mengelompokkan tabel log saja hanya menghasilkan baris untuk hari yang
     * ADA unduhannya. Hari sepi akan hilang begitu saja, dan grafik garis yang
     * melompati tanggal terbaca seolah-olah harinya tidak pernah ada —
     * kesalahan yang tidak kelihatan sebagai kesalahan.
     *
     * Dengan deret tanggal sebagai sisi kiri LEFT JOIN, hari tanpa unduhan
     * tetap muncul bernilai 0.
     *
     * Baris {@code PREVIEW} tidak ikut dihitung, alasannya sama dengan
     * {@link #countDownloadsSince(LocalDateTime)}. Penyaringnya ditaruh di dalam
     * ON, BUKAN di WHERE — pada LEFT JOIN keduanya berbeda. Di WHERE ia menyaring
     * setelah join selesai, sehingga hari yang hanya berisi pratinjau ikut
     * terbuang dan lubangnya kembali muncul di grafik; justru itu yang dicegah
     * seluruh {@code generate_series} di atas. Di dalam ON, harinya tetap ada
     * bernilai 0, dan itu memang keadaan sebenarnya.
     *
     * Dua CAST di bawah bukan hiasan. {@code generate_series} atas dua nilai
     * DATE menghasilkan {@code timestamptz}, yang sampai ke Java sebagai
     * {@link java.time.Instant} dan gagal dipetakan ke {@code LocalDate}.
     * Membangkitkan deretnya sebagai {@code timestamp} lalu memotongnya kembali
     * ke {@code date} menjaga tipenya tetap bebas zona waktu dari ujung ke
     * ujung — sama seperti kolom {@code downloaded_at} yang dibandingkan.
     *
     * <h2>Penyaring divisi juga di dalam ON, bukan WHERE</h2>
     *
     * Alasannya sama persis dengan penyaring {@code access_type} di atas. Di
     * WHERE, hari yang tidak punya unduhan dari divisi itu terbuang seluruhnya
     * dan lubangnya kembali muncul di grafik, padahal nol adalah jawaban yang
     * benar untuk hari itu.
     *
     * Penandanya boleh kosong, dan kosong berarti seluruh divisi. Bentuk itu
     * dipilih di sini meski dihindari di tempat lain, karena query ini hanya
     * punya satu pemanggil dan menyalin seluruh generate_series beserta CAST-nya
     * demi satu baris tambahan justru menghasilkan dua tempat yang harus dijaga
     * tetap sama.
     */
    @Query(value = """
            SELECT CAST(hari.tanggal AS date) AS log_date, COUNT(l.id) AS total
            FROM generate_series(CAST(:dari AS timestamp), CAST(:sampai AS timestamp), INTERVAL '1 day') AS hari(tanggal)
            LEFT JOIN download_log l
              ON l.downloaded_at >= hari.tanggal
             AND l.downloaded_at < hari.tanggal + INTERVAL '1 day'
             AND l.access_type = 'DOWNLOAD'
             AND (CAST(:divisionId AS uuid) IS NULL OR l.dataset_id IN (
                    SELECT d.id FROM dataset d WHERE d.division_id = CAST(:divisionId AS uuid)))
            GROUP BY hari.tanggal
            ORDER BY hari.tanggal
            """, nativeQuery = true)
    List<DailyDownloadCount> countPerDay(@Param("dari") LocalDate dari,
            @Param("sampai") LocalDate sampai,
            @Param("divisionId") UUID divisionId);
}
