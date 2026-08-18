package id.co.erdigma.satudata.modules.apiKey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApiKeyRequestCreateDTO {

    @NotBlank(message = "Nama key wajib diisi")
    @Size(max = 255, message = "Nama key maksimal 255 karakter")
    private String name;
}
