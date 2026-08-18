package id.co.erdigma.satudata.modules.dataset.dto;

import id.co.erdigma.satudata.annotation.PrefixedId;
import id.co.erdigma.satudata.enums.IdPrefix;
import java.util.UUID;

import lombok.Data;

/**
 * Berkas yang bisa diunduh. Sengaja TIDAK memuat storageKey maupun URL —
 * pengunduhan hanya lewat endpoint yang memeriksa karyawan dan mencatat audit.
 */
@Data
public class DatasetResourceResponse {
    @PrefixedId(IdPrefix.DATASET_RESOURCE)
    private UUID id;
    private String fileName;
    private String formatName;
    private String contentType;
    private long sizeBytes;
    private String checksumSha256;
}
