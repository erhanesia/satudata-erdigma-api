package id.co.erdigma.satudata.modules.dataset.dto;

import id.co.erdigma.satudata.annotation.PrefixedId;
import id.co.erdigma.satudata.enums.IdPrefix;
import java.util.UUID;

import lombok.Data;

/**
 * Berkas yang bisa diunduh. Sengaja TIDAK memuat storageKey maupun URL —
 * pengunduhan hanya lewat endpoint yang memeriksa karyawan dan mencatat audit.
 */
@Data
public class DatasetResourceResponse {
    @PrefixedId(IdPrefix.DATASET_RESOURCE)
    private UUID id;
    /** Nama yang ditulis penerbit, mis. "Kamus Kolom". Boleh kosong. */
    private String label;

    private String fileName;
    private String formatName;
    private String contentType;
    private long sizeBytes;
    private String checksumSha256;

    /**
     * True bila berkas inilah yang ditampilkan lebih dulu, dan yang angka
     * barisnya mewakili dataset di halaman katalog.
     *
     * Sejak changeset 37 ini BUKAN lagi berarti "satu-satunya yang punya
     * tabel" — berkas lain yang bisa dibaca pun punya tabelnya sendiri.
     */
    private boolean tableSource;

    /**
     * Ukuran tabel milik berkas ini. Nol berarti tidak ada tabel untuk
     * ditampilkan, jadi yang digambar adalah pratinjau dokumen atau kartu
     * unduhan.
     */
    private long rowCount;
    private int colCount;
}
