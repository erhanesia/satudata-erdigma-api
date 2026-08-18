package id.co.erdigma.satudata.modules.apiKey.dto;

import id.co.erdigma.satudata.annotation.PrefixedId;
import id.co.erdigma.satudata.enums.IdPrefix;
import java.time.LocalDateTime;
import java.util.UUID;

import id.co.erdigma.satudata.enums.ApiKeyStatus;

import lombok.Data;

/**
 * Tidak pernah memuat nilai key penuh — hanya awalannya. Desain memiliki tombol
 * "Tampilkan" yang mengungkap key utuh; itu tidak bisa dilayani karena server
 * hanya menyimpan hash. Lihat catatan di ApiKeyService.
 */
@Data
public class ApiKeyResponse {
    @PrefixedId(IdPrefix.API_KEY)
    private UUID id;
    private String name;
    private String keyPrefix;
    private String masked;
    private String tier;
    private int rateLimitPerMinute;
    private long usageCount;
    private ApiKeyStatus status;
    private LocalDateTime lastUsedAt;
    private LocalDateTime revokedAt;
    private LocalDateTime createdAt;
}
