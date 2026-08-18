package id.co.erdigma.satudata.modules.status.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.SQLDelete;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import id.co.erdigma.satudata.exception.ResourceNotFoundException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import lombok.Data;

/** Riwayat & jadwal maintenance untuk halaman Status Produk. */
@Data
@Entity
@SQLDelete(sql = "UPDATE incident SET deleted_at=CURRENT_TIMESTAMP WHERE id = ?")
@DynamicUpdate
@Table(name = "incident")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String title;
    private String tag;
    private String detail;
    private String occurredLabel;
    private LocalDateTime occurredAt;

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
