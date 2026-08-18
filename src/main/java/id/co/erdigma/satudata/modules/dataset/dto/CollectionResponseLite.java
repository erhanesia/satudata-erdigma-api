package id.co.erdigma.satudata.modules.dataset.dto;

import id.co.erdigma.satudata.annotation.PrefixedId;
import id.co.erdigma.satudata.enums.IdPrefix;
import java.util.UUID;

import lombok.Data;

@Data
public class CollectionResponseLite {
    @PrefixedId(IdPrefix.COLLECTION)
    private UUID id;
    private String slug;
    private String name;
}
