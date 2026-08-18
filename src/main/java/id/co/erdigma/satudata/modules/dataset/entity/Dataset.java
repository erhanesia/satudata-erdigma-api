package id.co.erdigma.satudata.modules.dataset.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.SQLDelete;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import id.co.erdigma.satudata.exception.ResourceNotFoundException;
import id.co.erdigma.satudata.modules.division.entity.Division;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Metadata katalog sebuah dataset — BUKAN isinya. Berkas asli (CSV/XLSX/PDF)
 * disimpan di penyimpanan berkas terpisah dan dirujuk lewat modul unduhan.
 */
@Data
@Entity
@SQLDelete(sql = "UPDATE dataset SET deleted_at=CURRENT_TIMESTAMP WHERE id = ?")
@DynamicUpdate
@Table(name = "dataset")
public class Dataset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Dipakai sebagai dataset_id di URL /datasets/{slug}/view */
    private String slug;
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "division_id", nullable = false)
    @Fetch(FetchMode.SELECT)
    private Division division;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = true)
    @Fetch(FetchMode.SELECT)
    private DatasetCollection collection;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "dataset_topics", joinColumns = @JoinColumn(name = "dataset_id"), inverseJoinColumns = @JoinColumn(name = "topic_id"))
    private List<Topic> topics = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "dataset_formats", joinColumns = @JoinColumn(name = "dataset_id"), inverseJoinColumns = @JoinColumn(name = "format_id"))
    private List<Format> formats = new ArrayList<>();

    @OneToMany(mappedBy = "dataset", fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<DatasetColumn> columns = new ArrayList<>();

    private String coverage;
    private String notes;
    private String disclaimer;
    private long rowCount;
    private int colCount;
    private String fileSize;
    private long downloads;
    private long apiCalls;

    /**
     * Kunjungan ke halaman detail dataset, bukan pengunjung unik. Dinaikkan
     * lewat {@code DatasetRepository#incrementViews} — UPDATE langsung, supaya
     * dua kunjungan bersamaan tidak saling menimpa dan {@code updated_at}
     * tidak ikut berubah setiap kali dataset dilihat orang.
     */
    private long views;

    /** Dataset streaming tanpa berkas unduhan, mis. trafik-aplikasi. */
    @Column(name = "is_realtime", nullable = false)
    private boolean realtime;

    private LocalDateTime lastUpdatedAt;

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
