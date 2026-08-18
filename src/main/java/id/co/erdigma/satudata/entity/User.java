package id.co.erdigma.satudata.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.SQLDelete;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import id.co.erdigma.satudata.enums.HrisPermissionLevel;
import id.co.erdigma.satudata.enums.Role;
import id.co.erdigma.satudata.exception.ResourceNotFoundException;
import id.co.erdigma.satudata.modules.division.entity.Division;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Bayangan lokal dari karyawan HRIS — bukan sumber kebenaran. Identitasnya
 * adalah {@code cognitoId} (klaim {@code sub} pada token Cognito), bukan
 * kolom {@code id}, supaya rujukan tetap sah setelah integrasi HRIS.
 */
@Data
@Entity
@SQLDelete(sql = "UPDATE users SET deleted_at=CURRENT_TIMESTAMP WHERE id = ?")
@DynamicUpdate
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = true)
    private String cognitoId;
    private String email;
    private String name;
    private String position;

    /** Peran portal — dasar otorisasi Satu Data. */
    @Enumerated(EnumType.STRING)
    private Role role;

    /** Hasil hitungan HRIS, direkam untuk pemetaan dan penelusuran. */
    @Enumerated(EnumType.STRING)
    @Column(name = "hris_permission_level")
    private HrisPermissionLevel hrisPermissionLevel;

    /** Bahan mentah asal HRIS menurunkan tingkat izin, mis. "Manager". */
    private String jobLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "division_id", nullable = true)
    @Fetch(FetchMode.SELECT)
    private Division division;

    public UUID getDivisionId() {
        return (division != null) ? division.getId() : null;
    }

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
