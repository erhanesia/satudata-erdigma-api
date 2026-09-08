package id.co.erdigma.satudata.modules.dataset.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.SQLDelete;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import id.co.erdigma.satudata.exception.ResourceNotFoundException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Satu berkas milik dataset. Dataset tanpa baris di sini berarti belum punya
 * berkas — front-end memakai itu untuk mematikan tombol Unduh.
 */
@Data
@Entity
@SQLDelete(sql = "UPDATE dataset_resource SET deleted_at=CURRENT_TIMESTAMP WHERE id = ?")
@DynamicUpdate
@Table(name = "dataset_resource")
public class DatasetResource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    @Fetch(FetchMode.SELECT)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Dataset dataset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "format_id", nullable = false)
    @Fetch(FetchMode.SELECT)
    private Format format;

    /**
     * Nama versi manusia, mis. "Kamus Kolom". Terpisah dari {@link #fileName}
     * yang harus tetap apa adanya supaya berkasnya bisa diunduh dengan benar.
     */
    private String label;

    private String fileName;
    private String contentType;
    private String storageProvider;
    private String storageKey;
    private long sizeBytes;
    private String checksumSha256;

    /**
     * Berkas inilah yang isinya dibaca menjadi {@code dataset_row}.
     *
     * Paling banyak satu per dataset. Tanpa penanda ini, front-end harus
     * menebak dari jenis berkas — dan tebakannya salah begitu satu dataset
     * memuat dua spreadsheet.
     */
    @Column(name = "is_table_source", nullable = false)
    private boolean tableSource;

    /**
     * Ukuran tabel milik berkas ini.
     *
     * Disimpan, bukan dihitung saat diminta. Halaman detail perlu tahu berkas
     * mana yang punya tabel sebelum menggambar apa pun; menghitungnya saat itu
     * juga berarti satu COUNT per berkas setiap kali halaman dibuka.
     *
     * Nol berarti berkasnya tidak punya tabel — PDF, Word, atau spreadsheet
     * yang isinya gagal dibaca.
     */
    @Column(name = "row_count", nullable = false)
    private long rowCount;

    @Column(name = "col_count", nullable = false)
    private int colCount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at", nullable = false)
    @LastModifiedDate
    private LocalDateTime updatedAt = LocalDateTime.now();
    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;

    @PreRemove
    public void preventDelete() {
        if (deletedAt != null) {
            throw new ResourceNotFoundException("Data Already Been Deleted");
        }
    }
}
