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
 * Divisi kontributor data. Kolom {@code hrisTeamId} adalah jembatan ke tabel
 * {@code team} milik hris-api, dan sejak changeset 42 seluruh isinya memang
 * berasal dari sana.
 *
 * Dulu bernama {@code hrisDepartementId}. Nama itu keliru: `departement` di
 * HRIS ternyata badan usaha — Gemilang Multazam, Erha Idea Cipta Karsa, dan
 * seterusnya — sedangkan unit kerja yang selama ini disebut "divisi" di sini
 * adalah `team`. Keduanya bukan susunan bertingkat; changeset 00023 di hris-api
 * menghapus {@code departement_id} dari tabel `team`.
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
    private UUID hrisTeamId;

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
