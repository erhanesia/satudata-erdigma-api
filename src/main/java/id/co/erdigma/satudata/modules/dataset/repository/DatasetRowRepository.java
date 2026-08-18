package id.co.erdigma.satudata.modules.dataset.repository;

import java.util.UUID;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import id.co.erdigma.satudata.modules.dataset.entity.DatasetRow;

@Repository
public interface DatasetRowRepository extends JpaRepository<DatasetRow, Long> {

    Page<DatasetRow> findAllByDatasetIdOrderByRowNumberAsc(UUID datasetId, Pageable pageable);

    long countByDatasetId(UUID datasetId);

    void deleteAllByDatasetId(UUID datasetId);

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
            WHERE dataset_id = :datasetId
            GROUP BY 1
            ORDER BY 2 DESC
            """, nativeQuery = true)
    List<Object[]> aggregateCount(@Param("datasetId") UUID datasetId, @Param("groupBy") String groupBy);

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
            WHERE dataset_id = :datasetId
            GROUP BY 1
            ORDER BY 3 DESC
            """, nativeQuery = true)
    List<Object[]> aggregateSum(@Param("datasetId") UUID datasetId,
            @Param("groupBy") String groupBy,
            @Param("metric") String metric);
}
