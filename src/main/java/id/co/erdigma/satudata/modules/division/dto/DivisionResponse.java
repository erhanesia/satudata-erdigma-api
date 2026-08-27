package id.co.erdigma.satudata.modules.division.dto;

import id.co.erdigma.satudata.annotation.PrefixedId;
import id.co.erdigma.satudata.enums.IdPrefix;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Data;

@Data
public class DivisionResponse {
    @PrefixedId(IdPrefix.DIVISION)
    private UUID id;
    private String code;
    private String name;
    private String logoBg;
    private long downloads;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
