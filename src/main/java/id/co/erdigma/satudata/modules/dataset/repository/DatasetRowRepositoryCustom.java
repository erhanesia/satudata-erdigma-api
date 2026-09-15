package id.co.erdigma.satudata.modules.dataset.repository;

import java.util.List;

import id.co.erdigma.satudata.modules.dataset.entity.DatasetRow;

public interface DatasetRowRepositoryCustom {

    /**
     * Menulis baris isi dataset dalam satu batch JDBC.
     *
     * Pengganti {@code saveAll} untuk impor. Id tetap dibuat database, dan
     * objek yang dikirim tidak menjadi entitas terkelola.
     */
    void insertAll(List<DatasetRow> rows);
}
