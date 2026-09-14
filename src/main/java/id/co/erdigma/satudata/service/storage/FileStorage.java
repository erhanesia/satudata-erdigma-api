package id.co.erdigma.satudata.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Titik jahit penyimpanan berkas. Seluruh aplikasi hanya mengenal antarmuka
 * ini; implementasinya bisa ditukar (lokal → S3 → Google Drive) tanpa mengubah
 * kode bisnis maupun skema database.
 *
 * Perhatikan {@link #open(String)} mengembalikan byte, BUKAN URL. Ini disengaja:
 * dengan begitu byte selalu melewati aplikasi sehingga audit log mencatat
 * "berkas benar-benar diunduh", bukan sekadar "tautan diterbitkan". Untuk portal
 * berisi data rahasia, perbedaan itu menentukan.
 */
public interface FileStorage {

    String getProviderName();

    StoredFile store(InputStream in, String storageKey, String contentType);

    /**
     * Menyimpan berkas yang sudah berada di disk. Implementasi bawaan cukup
     * membuka Path lalu mendelegasikan ke {@link #store(InputStream, String, String)};
     * implementasi yang bisa mengunggah langsung dari berkas meng-override agar
     * isinya tidak disalin dua kali.
     */
    default StoredFile storeFrom(Path source, String storageKey, String contentType) {
        try (InputStream in = Files.newInputStream(source)) {
            return store(in, storageKey, contentType);
        } catch (IOException e) {
            throw new IllegalStateException("Gagal membaca sumber: " + source, e);
        }
    }

    /**
     * Isi ASLI berkas, apa pun bentuknya di penyimpanan.
     *
     * Sebagian berkas disimpan dalam keadaan ter-gzip demi menghemat ruang,
     * ditandai akhiran {@code .gz} pada kuncinya. Implementasi WAJIB
     * membuka kompresinya di sini lewat {@link GzipStorage}, sehingga
     * pemanggil selalu menerima byte yang sama dengan yang diunggah.
     *
     * Kalau kewajiban itu dilanggar, yang terjadi bukan galat melainkan
     * importir CSV yang membaca byte gzip mentah lalu menyimpan sampah
     * sebagai isi tabel.
     */
    InputStream open(String storageKey);

    boolean exists(String storageKey);

    void delete(String storageKey);

    /** Apakah penyimpanan siap dipakai — dipakai halaman Status Produk. */
    boolean isHealthy();
}
