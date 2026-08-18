package id.co.erdigma.satudata.service.storage;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StoredFile {
    private String storageProvider;
    private String storageKey;
    private long sizeBytes;
    private String checksumSha256;
}
