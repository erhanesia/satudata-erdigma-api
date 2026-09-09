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
    /** Untuk baris gabungan, seluruh nama berkas dipisah titik koma. */
    private String fileName;

    /** Untuk baris gabungan, jumlah ukuran seluruh berkasnya. */
    private long sizeBytes;

    /**
     * Format berkas dalam peristiwa ini, dipisah koma, misalnya "CSV, DOCX".
     *
     * Kosong pada baris pembukaan dataset, yang memang tidak menyentuh berkas
     * mana pun. Kosong juga pada baris lama yang ditulis sebelum kolom ini
     * ada; antarmuka menyimpulkannya dari nama berkas untuk baris seperti itu.
     */
    private String formats;
    /** DOWNLOAD atau PREVIEW. */
    private String accessType;

    /** WEB atau API. */
    private String channel;

    private boolean agreementAccepted;
    private String ipAddress;
    private LocalDateTime downloadedAt;
}
