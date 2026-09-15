package id.co.erdigma.satudata.modules.dataset.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.modules.dataset.entity.DatasetRow;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Penulisan baris isi dataset lewat JDBC, bukan lewat Hibernate.
 *
 * <h2>Kenapa tidak {@code saveAll}</h2>
 *
 * {@link DatasetRow} memakai id {@code IDENTITY}, dan dengan itu Hibernate
 * tidak bisa menggabungkan INSERT: setiap baris menjadi satu perintah yang
 * menunggu id-nya dikembalikan. CSV 100.000 baris berarti 100.000 perjalanan
 * bolak-balik ke database, sekitar satu menit hanya untuk menulis.
 *
 * Di sini id tidak diminta kembali, jadi satu batch berangkat sekaligus, dan
 * {@code reWriteBatchedInserts} di application.yaml membuat driver
 * menggabungkannya menjadi INSERT banyak baris. Nilai tetap dikirim sebagai
 * parameter, kunci asing dan indeks unik tetap diperiksa database, dan id
 * tetap dibuat database.
 */
@Repository
@RequiredArgsConstructor
public class DatasetRowRepositoryImpl implements DatasetRowRepositoryCustom {

    /**
     * Seluruh kolom {@code dataset_row} selain {@code id}.
     *
     * Dicocokkan dengan {@code information_schema} oleh
     * DatasetRowInsertColumnsTest. Kolom baru yang boleh kosong akan terisi
     * kosong tanpa galat kalau daftar ini lupa diperbarui, dan tes itulah yang
     * memberi tahu.
     */
    static final List<String> INSERT_COLUMNS = List.of("dataset_id", "resource_id", "row_number", "data");

    private static final String INSERT_SQL = "INSERT INTO dataset_row (" + String.join(", ", INSERT_COLUMNS)
            + ") VALUES (?, ?, ?, ?::jsonb)";

    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;
    private final JsonMapper jsonMapper;

    /**
     * Wajib berada di dalam transaksi pemanggil, supaya baris dan berkas
     * asalnya tersimpan atau batal bersama-sama.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insertAll(List<DatasetRow> rows) {
        if (rows.isEmpty()) {
            return;
        }

        /*
          Flush lebih dulu, dan sengaja di sini, bukan di pemanggil.

          Berkas asal baris ini, dan pada unggahan baru datasetnya juga, baru
          saja disimpan lewat JPA, dan Hibernate menunda INSERT-nya sampai
          flush. JDBC tidak melihat antrean itu, sehingga tanpa baris ini kunci
          asing dataset_row_to_dataset atau dataset_row_to_resource menolak dan
          setiap unggahan CSV gagal. Keempat jalur impor lewat method ini, jadi
          tidak ada yang bisa lupa.
        */
        entityManager.flush();

        jdbcTemplate.batchUpdate(INSERT_SQL, rows, rows.size(), (statement, row) -> {
            statement.setObject(1, row.getDatasetId());
            statement.setObject(2, row.getResourceId());
            statement.setLong(3, row.getRowNumber());
            statement.setString(4, jsonMapper.writeValueAsString(row.getData()));
        });
    }
}
