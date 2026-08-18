package id.co.erdigma.satudata.modules.dataset.dto;

import id.co.erdigma.satudata.annotation.PrefixedId;
import id.co.erdigma.satudata.enums.IdPrefix;
import java.util.UUID;

import lombok.Data;

@Data
public class DatasetColumnResponse {
    @PrefixedId(IdPrefix.DATASET_COLUMN)
    private UUID id;
    private String machineName;
    private String displayName;
    private String dataType;
    private String unit;
    private String description;
    private int sortOrder;
}
