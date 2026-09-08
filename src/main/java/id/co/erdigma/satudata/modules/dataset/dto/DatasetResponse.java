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
    /** Siapa yang mengunggah. Null untuk dataset seed. */
    private UploaderResponse uploadedBy;
    private CollectionResponseLite collection;
    private List<String> topics;
    private List<String> formats;

    /**
     * Aturan siapa yang boleh melihat dataset ini.
     *
     * Kosong berarti terbuka untuk seluruh karyawan. Berisi berarti hanya yang
     * cocok dengan SALAH SATU aturan — plus ADMIN dan pengunggahnya — yang bisa
     * membuka, membaca isi tabel, dan mengunduhnya. Aturannya di
     * {@code DatasetAccessGuard}.
     *
     * Ketiga jenis aturan berdiri sejajar, bukan bertingkat: {@code JOB_LEVEL}
     * bersama {@code EMPLOYEE} berarti seluruh pemilik jenjang itu DAN karyawan
     * yang ditunjuk, bukan irisan keduanya.
     *
     * Nilai {@code POSITION} dan {@code EMPLOYEE} berupa UUID, bukan nama.
     * Penerjemahannya jadi nama dilakukan antarmuka, di tempat daftar HRIS-nya
     * memang sudah dimuat untuk isian pemilihnya.
     */
    private List<AccessRuleDTO> accessRules;
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
