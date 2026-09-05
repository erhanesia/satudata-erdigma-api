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
    /** Siapa yang mengunggah. Null untuk dataset seed. */
    private UploaderResponse uploadedBy;
    private List<String> topics;
    private List<String> formats;

    /**
     * Posisi jabatan yang boleh melihat dataset ini — kolom "Akses posisi" di
     * panel admin. Kosong berarti terbuka untuk seluruh karyawan.
     */
    private List<AccessRuleDTO> accessRules;

    /**
     * Berkas milik dataset ini. Ikut di daftar karena panel admin menampilkan
     * lencana jenis dan ukurannya per baris; daftar publik boleh mengabaikannya.
     */
    private List<DatasetResourceResponse> resources;
    private String coverage;
    private String notes;
    /** Dipakai tabel panel admin; daftar publik boleh mengabaikannya. */
    private long rowCount;
    private long downloads;
    private long apiCalls;
    private long views;
    private boolean realtime;
    private LocalDateTime lastUpdatedAt;

    /** Kapan dataset ini pertama kali masuk katalog — kolom "Diunggah". */
    private LocalDateTime createdAt;
}
