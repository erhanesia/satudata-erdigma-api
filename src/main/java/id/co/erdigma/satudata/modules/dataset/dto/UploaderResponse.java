package id.co.erdigma.satudata.modules.dataset.dto;

import java.util.UUID;

import id.co.erdigma.satudata.annotation.PrefixedId;
import id.co.erdigma.satudata.enums.IdPrefix;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Identitas pengunggah dataset, seperlunya saja untuk kolom "Diunggah oleh" di
 * panel admin.
 *
 * Sengaja tidak memakai {@code UserResponse} yang lengkap: daftar dataset tidak
 * perlu tahu peran portal, tingkat izin HRIS, maupun jabatan seseorang, dan
 * membawanya berarti menyebarkan data kepegawaian ke tempat yang tidak
 * membutuhkannya.
 */
@Data
public class UploaderResponse {

    @PrefixedId(IdPrefix.USER)
    private UUID id;

    @Schema(example = "M. Fahrega Ridwan")
    private String name;

    @Schema(description = "Jabatan, apa adanya dari HRIS.", example = "Project Manager Data & IT")
    private String position;

    @Schema(description = "Kode divisi pengunggah saat itu.", example = "DNA")
    private String divisionCode;
}
