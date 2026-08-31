package id.co.erdigma.satudata.modules.division.dto;

import id.co.erdigma.satudata.annotation.PrefixedId;
import id.co.erdigma.satudata.enums.IdPrefix;
import java.util.UUID;

import lombok.Data;

@Data
public class DivisionResponseLite {
    @PrefixedId(IdPrefix.DIVISION)
    private UUID id;
    private String code;
    private String name;
}
