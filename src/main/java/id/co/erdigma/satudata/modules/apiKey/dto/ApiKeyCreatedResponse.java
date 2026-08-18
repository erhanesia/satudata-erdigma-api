package id.co.erdigma.satudata.modules.apiKey.dto;

import lombok.Data;

/**
 * Satu-satunya respons yang pernah memuat nilai key penuh. Ditampilkan sekali
 * saat pembuatan; server tidak bisa menampilkannya lagi setelah ini.
 */
@Data
public class ApiKeyCreatedResponse {
    private ApiKeyResponse key;
    private String plainKey;
    private String warning = "Simpan key ini sekarang. Nilai penuhnya tidak dapat ditampilkan lagi.";
}
