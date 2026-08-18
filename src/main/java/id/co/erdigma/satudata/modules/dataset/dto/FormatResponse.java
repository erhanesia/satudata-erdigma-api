package id.co.erdigma.satudata.modules.dataset.dto;

import id.co.erdigma.satudata.annotation.PrefixedId;
import id.co.erdigma.satudata.enums.IdPrefix;
import java.util.UUID;

import lombok.Data;

@Data
public class FormatResponse {
    @PrefixedId(IdPrefix.FORMAT)
    private UUID id;
    private String name;
    private int sortOrder;
}
