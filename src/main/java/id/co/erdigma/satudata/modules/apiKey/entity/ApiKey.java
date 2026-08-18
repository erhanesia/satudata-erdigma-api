package id.co.erdigma.satudata.modules.apiKey.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.SQLDelete;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.ApiKeyStatus;
import id.co.erdigma.satudata.exception.ResourceNotFoundException;

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
 * Kredensial untuk pemanggil non-manusia (skrip, BI, sistem lain).
 *
 * Hanya hash SHA-256 yang disimpan. Nilai penuh ditampilkan SEKALI saat dibuat
 * dan setelah itu tidak bisa diambil lagi dari server — kalau hilang, buat baru.
 */
@Data
@Entity
@SQLDelete(sql = "UPDATE api_key SET deleted_at=CURRENT_TIMESTAMP WHERE id = ?")
@DynamicUpdate
@Table(name = "api_key")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @Fetch(FetchMode.SELECT)
    private User user;

    private String name;
    private String keyHash;
    private String keyPrefix;
    /**
     * Nilai kunci terenkripsi AES-256-GCM beserta nonce-nya, supaya tombol
     * "Tampilkan" di Dasbor Akun bisa dilayani. Keduanya nullable karena kunci
     * yang dibuat sebelum changeset 00022 tidak memilikinya.
     *
     * Jangan pernah ikut dipetakan ke DTO respons daftar — nilai ini hanya boleh
     * keluar lewat endpoint reveal, satu kunci pada satu waktu.
     */
    private String keyCipher;
    private String keyNonce;
    private String tier;
    private int rateLimitPerMinute;
    private long usageCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ApiKeyStatus status = ApiKeyStatus.ACTIVE;

    private LocalDateTime lastUsedAt;
    private LocalDateTime revokedAt;

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
