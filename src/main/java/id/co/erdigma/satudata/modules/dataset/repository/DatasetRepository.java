package id.co.erdigma.satudata.modules.dataset.repository;

import java.util.Optional;
import java.util.UUID;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import id.co.erdigma.satudata.modules.dataset.entity.Dataset;

@Repository
public interface DatasetRepository extends JpaRepository<Dataset, UUID>, JpaSpecificationExecutor<Dataset> {

    Optional<Dataset> findBySlugAndDeletedAtIsNull(String slug);

    /**
     * Sengaja TIDAK menyaring {@code deletedAt}. Kolom {@code slug} punya UNIQUE
     * constraint di database yang tidak peduli pada soft delete, jadi dataset
     * yang sudah "dihapus" tetap memegang slug-nya. Memeriksa hanya baris hidup
     * akan lolos di sini lalu gagal saat INSERT dengan galat constraint yang
     * tidak berarti apa-apa bagi penerbit.
     */
    boolean existsBySlug(String slug);

    List<Dataset> findAllByCollectionIdAndDeletedAtIsNullOrderByTitleAsc(UUID collectionId);

    long countByDeletedAtIsNull();

    /*
      Hitungan yang sama, dibatasi satu divisi, untuk dasbor panel admin.

      Sengaja TIDAK dijadikan parameter yang boleh null pada metode di atas.
      Metode di atas melayani beranda portal yang memang harus menghitung
      seluruh katalog, dan menyatukan keduanya berarti satu pemanggil yang lupa
      mengirim parameternya diam-diam mengubah arti angka di halaman yang
      dilihat seluruh karyawan.
    */
    long countByDeletedAtIsNullAndDivisionId(UUID divisionId);

    @Query("""
            SELECT COALESCE(SUM(d.downloads), 0) FROM Dataset d
            WHERE d.deletedAt IS NULL AND d.division.id = :divisionId
            """)
    long sumDownloadsByDivision(@Param("divisionId") UUID divisionId);

    /** Kembaran divisi dari {@link #countContributor()}. */
    @Query("""
            SELECT COUNT(DISTINCT d.uploadedBy.id) FROM Dataset d
            WHERE d.deletedAt IS NULL AND d.uploadedBy IS NOT NULL
              AND d.division.id = :divisionId
            """)
    long countContributorByDivision(@Param("divisionId") UUID divisionId);

    @Query("SELECT COALESCE(SUM(d.downloads), 0) FROM Dataset d WHERE d.deletedAt IS NULL")
    long sumDownloads();

    @Query("SELECT COALESCE(SUM(d.apiCalls), 0) FROM Dataset d WHERE d.deletedAt IS NULL")
    long sumApiCalls();

    /** Kembaran divisi dari {@link #sumApiCalls()}. */
    @Query("""
            SELECT COALESCE(SUM(d.apiCalls), 0) FROM Dataset d
            WHERE d.deletedAt IS NULL AND d.division.id = :divisionId
            """)
    long sumApiCallsByDivision(@Param("divisionId") UUID divisionId);

    @Query("SELECT COALESCE(SUM(d.views), 0) FROM Dataset d WHERE d.deletedAt IS NULL")
    long sumViews();

    /** Kembaran divisi dari {@link #sumViews()}. */
    @Query("""
            SELECT COALESCE(SUM(d.views), 0) FROM Dataset d
            WHERE d.deletedAt IS NULL AND d.division.id = :divisionId
            """)
    long sumViewsByDivision(@Param("divisionId") UUID divisionId);

    /**
     * Menaikkan penghitung kunjungan satu langkah.
     *
     * UPDATE langsung, bukan {@code setViews(getViews() + 1)} lalu {@code save},
     * karena dua alasan yang keduanya nyata di sini:
     *
     *  - **Tidak ada pembaruan yang hilang.** Dua orang membuka dataset yang
     *    sama pada saat bersamaan sama-sama membaca nilai lama, lalu keduanya
     *    menulis nilai lama + 1 — satu kunjungan menguap. Penambahan di sisi
     *    database tidak punya celah itu.
     *  - **{@code updated_at} tidak ikut bergerak.** Kolom itu dipetakan
     *    {@code @LastModifiedDate}; kalau entity-nya yang disimpan, setiap orang
     *    yang sekadar melihat dataset akan membuatnya tampak baru saja
     *    diperbarui.
     */
    @Modifying
    @Query("UPDATE Dataset d SET d.views = d.views + 1 WHERE d.id = :id")
    void incrementViews(@Param("id") UUID id);

    @Query("""
            SELECT COUNT(DISTINCT r.dataset.id) FROM DatasetResource r
            WHERE r.deletedAt IS NULL AND r.dataset.deletedAt IS NULL
            """)
    long countWithResource();

    /**
     * Kembaran divisi dari {@link #countWithResource()}.

     * Divisi dibaca dari DATASET-nya, bukan dari berkasnya, karena berkas
     * tidak punya divisi sendiri: ia selalu milik dataset yang memuatnya.
     */
    @Query("""
            SELECT COUNT(DISTINCT r.dataset.id) FROM DatasetResource r
            WHERE r.deletedAt IS NULL AND r.dataset.deletedAt IS NULL
              AND r.dataset.division.id = :divisionId
            """)
    long countWithResourceByDivision(@Param("divisionId") UUID divisionId);

    @Query("""
            SELECT d FROM Dataset d
            WHERE d.deletedAt IS NULL AND d.division.code = :divisionCode
            ORDER BY d.lastUpdatedAt DESC
            """)
    List<Dataset> findAllByDivisionCode(@Param("divisionCode") String divisionCode);

    /**
     * Isi kartu "Kontributor": berapa ORANG yang pernah menerbitkan dataset,
     * bukan berapa dataset yang punya pemilik. Dataset seed lama yang
     * uploaded_by-nya kosong tidak ikut terhitung.
     */
    @Query("""
            SELECT COUNT(DISTINCT d.uploadedBy.id) FROM Dataset d
            WHERE d.deletedAt IS NULL AND d.uploadedBy IS NOT NULL
            """)
    long countContributor();
}
