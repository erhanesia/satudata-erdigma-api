package id.co.erdigma.satudata.modules.dataset.dto;

import id.co.erdigma.satudata.annotation.PrefixedId;
import id.co.erdigma.satudata.enums.IdPrefix;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import id.co.erdigma.satudata.modules.division.dto.DivisionResponseLite;

import lombok.Data;

/**
 * Bentuk kartu dataset di halaman Datasets — tanpa daftar kolom, supaya
 * listing tidak memicu query tambahan per baris.
 */
@Data
public class DatasetResponseLite {
    @PrefixedId(IdPrefix.DATASET)
    private UUID id;
    private String slug;
    private String title;
    private DivisionResponseLite division;
    private List<String> topics;
    private List<String> formats;
    private String coverage;
    private String notes;
    private long downloads;
    private long apiCalls;
    private long views;
    private boolean realtime;
    private LocalDateTime lastUpdatedAt;
}
