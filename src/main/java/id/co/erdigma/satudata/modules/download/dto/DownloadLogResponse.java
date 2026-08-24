package id.co.erdigma.satudata.modules.download.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * Satu baris tab "Log unduhan".
 *
 * {@code cognitoId} dan {@code userAgent} sengaja TIDAK ikut. Keduanya ada di
 * tabel dan berguna saat menelusuri insiden, tapi tidak ada gunanya di layar —
 * dan mengirim identitas mesin ke browser tanpa keperluan hanya memperluas
 * permukaan kebocoran.
 */
@Data
public class DownloadLogResponse {
    private Long id;
    private String userName;
    private String userEmail;
    private String divisionCode;
    private String datasetSlug;
    private String fileName;
    private long sizeBytes;
    /** DOWNLOAD atau PREVIEW. */
    private String accessType;

    /** WEB atau API. */
    private String channel;

    private boolean agreementAccepted;
    private String ipAddress;
    private LocalDateTime downloadedAt;
}
