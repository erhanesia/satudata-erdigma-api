package id.co.erdigma.satudata.modules.dataset.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import id.co.erdigma.satudata.annotation.PrefixedId;
import id.co.erdigma.satudata.enums.IdPrefix;

import lombok.Data;

/**
 * Isi tabel dataset untuk Data Explorer. Paginasi dikerjakan di server —
 * 10.000 baris tidak pernah dikirim sekaligus ke browser.
 */
@Data
public class DatastoreResponse {
    private String slug;

    /**
     * Berkas yang isinya dijawab di sini.
     *
     * Satu dataset bisa punya beberapa tabel, jadi jawaban tanpa penunjuk
     * berkas tidak bisa dipastikan berasal dari mana. Kosong berarti dataset
     * ini memang belum punya tabel sama sekali.
     */
    @PrefixedId(IdPrefix.DATASET_RESOURCE)
    private UUID resourceId;

    private long totalRows;
    private int page;
    private int size;
    private int totalPages;
    private List<DatasetColumnResponse> columns;
    private List<Map<String, Object>> rows;
}
