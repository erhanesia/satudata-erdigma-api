package id.co.erdigma.satudata.modules.dataset.dto;

import id.co.erdigma.satudata.annotation.PrefixedId;
import id.co.erdigma.satudata.enums.IdPrefix;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import id.co.erdigma.satudata.modules.division.dto.DivisionResponseLite;

import lombok.Data;

@Data
public class DatasetResponse {
    @PrefixedId(IdPrefix.DATASET)
    private UUID id;
    private String slug;
    private String title;
    private DivisionResponseLite division;
    private CollectionResponseLite collection;
    private List<String> topics;
    private List<String> formats;
    private String coverage;
    private String notes;
    private String disclaimer;
    private long rowCount;
    private int colCount;
    private String fileSize;
    private long downloads;
    private long apiCalls;
    private long views;
    private boolean realtime;
    private LocalDateTime lastUpdatedAt;
    private List<DatasetColumnResponse> columns;

    /** Kosong berarti dataset belum punya berkas — tombol Unduh dimatikan. */
    private List<DatasetResourceResponse> resources;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
