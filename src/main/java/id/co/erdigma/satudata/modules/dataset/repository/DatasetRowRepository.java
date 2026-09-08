package id.co.erdigma.satudata.modules.dataset.repository;

import java.util.UUID;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import id.co.erdigma.satudata.modules.dataset.entity.DatasetRow;

@Repository
public interface DatasetRowRepository extends JpaRepository<DatasetRow, Long> {

    Page<DatasetRow> findAllByResourceIdOrderByRowNumberAsc(UUID resourceId, Pageable pageable);

    long countByResourceId(UUID resourceId);

    long countByDatasetId(UUID datasetId);

    /**
     * Mengosongkan isi tabel satu berkas.
     *
     * Sengaja perintah massal, bukan penghapusan turunan. Dua alasannya:
     *
     * 1. **Waktunya.** Perintah ini berangkat saat dipanggil. Penghapusan
     *    turunan hanya mengantre sampai flush, padahal baris penggantinya
     *    harus masuk lebih dulu untuk mengambil id dari database — dan indeks
     *    unik (resource_id, row_number) menolak keduanya berada bersamaan.
     * 2. **Biayanya.** Yang turunan memuat setiap baris ke memori sebelum
     *    menghapusnya. Untuk berkas 61.876 baris itu 61.876 objek yang dibuat
     *    hanya untuk dibuang.
     */
    @Modifying
    @Query("DELETE FROM DatasetRow r WHERE r.resourceId = :resourceId")
    void deleteAllByResourceId(@Param("resourceId") UUID resourceId);

    /**
     * Dipakai saat seluruh dataset dibuang. Baris tetap menyimpan dataset_id
     * di samping resource_id supaya pembersihan seperti ini tidak perlu
     * menelusuri daftar berkasnya satu per satu lebih dulu.
     */
    @Modifying
    @Query("DELETE FROM DatasetRow r WHERE r.datasetId = :datasetId")
    void deleteAllByDatasetId(@Param("datasetId") UUID datasetId);

    /**
     * Jumlah baris per nilai satu kolom.
     *
     * GROUP BY memakai nomor ordinal, bukan mengulang ekspresinya. Parameter
     * bernama yang sama dipakai dua kali akan menjadi dua placeholder berbeda
     * di sisi Postgres, sehingga GROUP BY tidak dianggap cocok dengan SELECT.
     */
    @Query(value = """
            SELECT data ->> :groupBy AS label, COUNT(*) AS cnt
            FROM dataset_row
            WHERE resource_id = :resourceId
            GROUP BY 1
            ORDER BY 2 DESC
            """, nativeQuery = true)
    List<Object[]> aggregateCount(@Param("resourceId") UUID resourceId, @Param("groupBy") String groupBy);

    /**
     * Sama seperti di atas plus total satu kolom numerik. Nilai yang bukan angka
     * dianggap 0 — isi JSONB tidak dijamin bertipe, jadi cast langsung bisa gagal.
     */
    @Query(value = """
            SELECT data ->> :groupBy AS label,
                   COUNT(*) AS cnt,
                   COALESCE(SUM(CASE WHEN data ->> :metric ~ '^-?[0-9]+(\\.[0-9]+)?$'
                                     THEN (data ->> :metric)::numeric ELSE 0 END), 0) AS total
            FROM dataset_row
            WHERE resource_id = :resourceId
            GROUP BY 1
            ORDER BY 3 DESC
            """, nativeQuery = true)
    List<Object[]> aggregateSum(@Param("resourceId") UUID resourceId,
            @Param("groupBy") String groupBy,
            @Param("metric") String metric);
}
