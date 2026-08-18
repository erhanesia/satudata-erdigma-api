package id.co.erdigma.satudata.modules.division.entity;

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

/**
 * Divisi kontributor data. Kolom {@code hrisDepartementId} adalah jembatan ke
 * tabel {@code departement} milik hris-api — diisi saat integrasi HRIS.
 */
@Data
@Entity
@SQLDelete(sql = "UPDATE division SET deleted_at=CURRENT_TIMESTAMP WHERE id = ?")
@DynamicUpdate
@Table(name = "division")
public class Division {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String code;
    private String name;
    private String logoBg;
    private UUID hrisDepartementId;
    private long apiCalls;
    private long downloads;

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
