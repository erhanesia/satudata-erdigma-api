package id.co.erdigma.satudata.modules.dataset.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * Isi tabel dataset untuk Data Explorer. Paginasi dikerjakan di server —
 * 10.000 baris tidak pernah dikirim sekaligus ke browser.
 */
@Data
public class DatastoreResponse {
    private String slug;
    private long totalRows;
    private int page;
    private int size;
    private int totalPages;
    private List<DatasetColumnResponse> columns;
    private List<Map<String, Object>> rows;
}
