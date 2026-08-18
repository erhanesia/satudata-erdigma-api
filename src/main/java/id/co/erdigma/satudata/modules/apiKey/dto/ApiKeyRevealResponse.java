package id.co.erdigma.satudata.modules.apiKey.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Balasan tombol "Tampilkan" di Dasbor Akun.
 *
 * Sengaja hanya memuat nilai kunci, tanpa metadata lain — supaya respons ini
 * tidak pernah dipakai sebagai sumber data umum dan mudah dikenali saat menelaah
 * log atau lalu lintas jaringan.
 */
@Data
public class ApiKeyRevealResponse {

    @Schema(description = "Nilai API key seutuhnya, hasil dekripsi.", example = "erd_live_9f2a8c31d4e7b0a25f6c9081b3e7a4d2")
    private String plainKey;
}
