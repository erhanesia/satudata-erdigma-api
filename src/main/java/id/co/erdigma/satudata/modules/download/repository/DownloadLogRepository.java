package id.co.erdigma.satudata.modules.download.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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

    Page<DownloadLog> findAllByOrderByDownloadedAtDesc(Pageable pageable);

    Page<DownloadLog> findAllByDatasetIdOrderByDownloadedAtDesc(UUID datasetId, Pageable pageable);

    Page<DownloadLog> findAllByCognitoIdOrderByDownloadedAtDesc(String cognitoId, Pageable pageable);

    Page<DownloadLog> findAllByDownloadedAtBetweenOrderByDownloadedAtDesc(
            LocalDateTime dari, LocalDateTime sampai, Pageable pageable);

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
     */
    @Query(value = """
            SELECT CAST(hari.tanggal AS date) AS log_date, COUNT(l.id) AS total
            FROM generate_series(CAST(:dari AS timestamp), CAST(:sampai AS timestamp), INTERVAL '1 day') AS hari(tanggal)
            LEFT JOIN download_log l
              ON l.downloaded_at >= hari.tanggal
             AND l.downloaded_at < hari.tanggal + INTERVAL '1 day'
             AND l.access_type = 'DOWNLOAD'
            GROUP BY hari.tanggal
            ORDER BY hari.tanggal
            """, nativeQuery = true)
    List<DailyDownloadCount> countPerDay(@Param("dari") LocalDate dari,
            @Param("sampai") LocalDate sampai);
}
