package id.co.erdigma.satudata.modules.audit.entity;

import java.time.LocalDateTime;

import id.co.erdigma.satudata.enums.AuditAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Jejak audit tindakan terhadap dataset.
 *
 * Sama seperti {@code DownloadLog} dan karena alasan yang sama: TIDAK memakai
 * soft delete, dan TIDAK memakai foreign key ke users maupun dataset. Pelaku
 * dan objeknya disimpan sebagai nilai, bukan relasi, supaya barisnya tetap
 * terbaca setelah orangnya resign atau datasetnya dihapus — justru saat itulah
 * catatan ini paling dibutuhkan.
 */
@Data
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Kosong berarti tindakan dijalankan sistem, bukan orang. */
    @Column(name = "actor_cognito_id")
    private String actorCognitoId;

    @Column(name = "actor_name")
    private String actorName;

    @Column(name = "actor_division_code")
    private String actorDivisionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private AuditAction action;

    /** Sekarang selalu "dataset". Lihat catatan changeset 00025. */
    @Column(name = "object_type", nullable = false)
    private String objectType;

    @Column(name = "object_slug")
    private String objectSlug;

    /** Judul objek saat tindakan terjadi — judul bisa berubah setelahnya. */
    @Column(name = "object_label")
    private String objectLabel;

    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt = LocalDateTime.now();
}
