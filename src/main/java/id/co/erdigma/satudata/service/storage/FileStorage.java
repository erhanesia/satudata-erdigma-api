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

    InputStream open(String storageKey);

    boolean exists(String storageKey);

    void delete(String storageKey);

    /** Apakah penyimpanan siap dipakai — dipakai halaman Status Produk. */
    boolean isHealthy();
}
