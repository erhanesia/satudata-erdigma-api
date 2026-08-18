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
 * Skema kolom sebuah dataset — yang tampil di tab "Kolom" halaman detail.
 */
@Data
@Entity
@SQLDelete(sql = "UPDATE dataset_column SET deleted_at=CURRENT_TIMESTAMP WHERE id = ?")
@DynamicUpdate
@Table(name = "dataset_column")
public class DatasetColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    @Fetch(FetchMode.SELECT)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Dataset dataset;

    private String machineName;
    private String displayName;
    private String dataType;
    private String unit;
    private String description;
    private int sortOrder;

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
