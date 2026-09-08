package id.co.erdigma.satudata.modules.download.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Jejak audit unduhan.
 *
 * Sengaja TIDAK memakai soft delete dan TIDAK memakai foreign key ke users —
 * baris audit harus tetap utuh dan terbaca walaupun user atau dataset-nya
 * kemudian dihapus. Identitas disimpan sebagai nilai, bukan relasi.
 */
@Data
@Entity
@Table(name = "download_log")
public class DownloadLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String cognitoId;
    private String userName;
    private String userEmail;
    private String divisionCode;

    @Column(name = "dataset_id", nullable = false)
    private UUID datasetId;
    private String datasetSlug;

    @Column(name = "resource_id")
    private UUID resourceId;
    private String fileName;
    private long sizeBytes;

    /**
     * DOWNLOAD atau PREVIEW. Dibedakan karena pratinjau tidak melewati modal
     * persetujuan — menyamakan keduanya membuat kolom persetujuan berbunyi
     * "tidak" pada ratusan baris dan terbaca seolah orang mengunduh tanpa
     * menyetujui apa pun.
     */
    @Column(name = "access_type", nullable = false, length = 20)
    private String accessType = "DOWNLOAD";

    /**
     * WEB atau API. Disimpan, bukan ditulis tetap di antarmuka — begitu jalur
     * kunci mesin aktif, kolom yang dikarang akan tetap berbunyi "Web" untuk
     * unduhan yang sebenarnya lewat mesin.
     */
    @Column(name = "channel", nullable = false, length = 20)
    private String channel = "WEB";

    private boolean agreementAccepted;
    private String ipAddress;
    private String userAgent;

    @Column(name = "downloaded_at", nullable = false, updatable = false)
    private LocalDateTime downloadedAt = LocalDateTime.now();
}
