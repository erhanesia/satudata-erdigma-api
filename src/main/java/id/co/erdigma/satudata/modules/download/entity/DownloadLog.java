package id.co.erdigma.satudata.modules.download.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Jejak audit unduhan.
 *
 * Sengaja TIDAK memakai soft delete dan TIDAK memakai foreign key ke users —
 * baris audit harus tetap utuh dan terbaca walaupun user atau dataset-nya
 * kemudian dihapus. Identitas disimpan sebagai nilai, bukan relasi.
 */
@Data
@Entity
@Table(name = "download_log")
public class DownloadLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String cognitoId;
    private String userName;
    private String userEmail;
    private String divisionCode;

    @Column(name = "dataset_id", nullable = false)
    private UUID datasetId;
    private String datasetSlug;

    /**
     * Berkas yang diakses, bila peristiwanya menyangkut satu berkas saja.
     *
     * Kosong pada dua keadaan yang sah: baris pembukaan dataset, yang memang
     * tidak menyentuh berkas mana pun, dan baris unduhan yang menggabungkan
     * beberapa berkas sekaligus, yang tidak bisa menunjuk salah satunya tanpa
     * berbohong tentang dua sisanya.
     */
    @Column(name = "resource_id")
    private UUID resourceId;

    /** Untuk baris gabungan, nama seluruh berkasnya dipisah titik koma. */
    private String fileName;

    /** Untuk baris gabungan, jumlah ukuran seluruh berkas dalam aksi itu. */
    private long sizeBytes;

    /**
     * Format berkas dalam peristiwa ini, dipisah koma, misalnya "CSV, DOCX".
     *
     * <h2>Kenapa tidak disimpulkan dari nama berkas</h2>
     *
     * Nama berkas tidak selalu berujung ekstensi yang benar, sedangkan format
     * sebenarnya sudah disimpan tersendiri sejak berkasnya diunggah.
     * Menyimpulkannya dari nama berarti menebak sesuatu yang sudah diketahui,
     * dan tebakannya meleset persis pada berkas yang namanya tidak biasa.
     *
     * <h2>Kosong pada baris pembukaan dataset</h2>
     *
     * Membuka dataset bukan mengakses berkas tertentu, jadi tidak ada format
     * yang bisa disebut. Kolomnya dikosongkan, bukan diisi tanda apa pun,
     * supaya "tidak ada format" tidak tertukar dengan "formatnya tidak
     * dikenali".
     */
    @Column(name = "formats", length = 200)
    private String formats;

    /**
     * Penanda satu aksi unduh, dibuat pemanggil.
     *
     * <h2>Kenapa penanda, bukan kedekatan waktu</h2>
     *
     * Beberapa berkas dalam satu penekanan tombol sampai ke server sebagai
     * permintaan terpisah, dan tidak ada apa pun di permintaannya yang
     * menyatakan ketiganya satu peristiwa. Versi pertama menebaknya dari
     * jarak waktu, dan tebakan itu salah di kedua arah.
     *
     * Front-end mengunduh BERURUTAN, menunggu satu berkas selesai sebelum
     * meminta berikutnya, jadi jaraknya sama dengan lamanya mengunduh. Pada
     * berkas ratusan megabita itu puluhan detik, dan jendela yang pendek
     * memecah satu aksi menjadi banyak baris. Jendela yang panjang
     * sebaliknya menelan unduhan berikutnya yang benar-benar disengaja.
     *
     * Penanda menghapus tebakan itu sepenuhnya: dua klik terpisah selalu dua
     * baris walau jaraknya sepersepuluh ribu detik, dan satu klik selalu satu
     * baris walau berkasnya lama sekali.
     *
     * Kosong untuk pemanggil yang tidak menyertakannya, dan untuk baris
     * pembukaan dataset yang memang bukan aksi unduh.
     */
    @Column(name = "action_id")
    private UUID actionId;

    /**
     * DOWNLOAD atau PREVIEW. Dibedakan karena pratinjau tidak melewati modal
     * persetujuan — menyamakan keduanya membuat kolom persetujuan berbunyi
     * "tidak" pada ratusan baris dan terbaca seolah orang mengunduh tanpa
     * menyetujui apa pun.
     */
    @Column(name = "access_type", nullable = false, length = 20)
    private String accessType = "DOWNLOAD";

    /**
     * WEB atau API. Disimpan, bukan ditulis tetap di antarmuka — begitu jalur
     * kunci mesin aktif, kolom yang dikarang akan tetap berbunyi "Web" untuk
     * unduhan yang sebenarnya lewat mesin.
     */
    @Column(name = "channel", nullable = false, length = 20)
    private String channel = "WEB";

    private boolean agreementAccepted;
    private String ipAddress;
    private String userAgent;

    @Column(name = "downloaded_at", nullable = false, updatable = false)
    private LocalDateTime downloadedAt = LocalDateTime.now();
}
