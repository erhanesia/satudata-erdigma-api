package id.co.erdigma.satudata.modules.audit.dto;

import java.time.LocalDateTime;

import id.co.erdigma.satudata.enums.AuditAction;

import lombok.Data;

/**
 * Satu baris "Aktivitas terakhir".
 *
 * Bidangnya sengaja terpisah-pisah, bukan satu kalimat jadi. Panel admin
 * mewarnai kata tindakannya sendiri dan menyusun kalimatnya di sisi tampilan;
 * kalau back-end mengirim string yang sudah dirangkai, tidak ada cara
 * menandai bagian mana yang mana tanpa mengurai teks lagi.
 */
@Data
public class AuditLogResponse {
    private Long id;
    private String actorName;
    private String actorDivisionCode;
    private AuditAction action;
    private String objectType;
    private String objectSlug;
    private String objectLabel;
    private String detail;
    private LocalDateTime recordedAt;
}
