package id.co.erdigma.satudata.modules.collection.dto;

import id.co.erdigma.satudata.annotation.PrefixedId;
import id.co.erdigma.satudata.enums.IdPrefix;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import id.co.erdigma.satudata.modules.division.dto.DivisionResponseLite;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetResponseLite;

import lombok.Data;

@Data
public class CollectionResponse {
    @PrefixedId(IdPrefix.COLLECTION)
    private UUID id;
    private String slug;
    private String name;
    private String description;
    private DivisionResponseLite division;
    private long datasetCount;

    /** Hanya terisi pada endpoint detail. */
    private List<DatasetResponseLite> datasets;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
