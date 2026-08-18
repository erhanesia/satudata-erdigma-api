package id.co.erdigma.satudata.service.storage;

import java.io.InputStream;

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

    InputStream open(String storageKey);

    boolean exists(String storageKey);

    void delete(String storageKey);

    /** Apakah penyimpanan siap dipakai — dipakai halaman Status Produk. */
    boolean isHealthy();
}
