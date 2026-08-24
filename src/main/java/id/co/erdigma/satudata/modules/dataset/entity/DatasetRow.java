package id.co.erdigma.satudata.modules.dataset.entity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Satu baris isi dataset, disimpan generik sebagai JSONB. Bentuk kolomnya
 * dijelaskan {@link DatasetColumn}, bukan oleh struktur tabel ini — sehingga
 * dataset apa pun muat tanpa migrasi.
 */
@Data
@Entity
@Table(name = "dataset_row")
public class DatasetRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_id", nullable = false)
    private UUID datasetId;

    /**
     * Berkas asal baris ini.
     *
     * Satu dataset boleh memuat beberapa berkas yang sama-sama punya tabel —
     * misalnya CSV dan Excel berdampingan. Tanpa penunjuk ini, isi keduanya
     * bercampur di satu tabel dan tidak ada cara memisahkannya lagi.
     */
    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "row_number", nullable = false)
    private long rowNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> data = new LinkedHashMap<>();
}
