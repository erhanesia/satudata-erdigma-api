package id.co.erdigma.satudata.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import id.co.erdigma.satudata.exception.ResourceNotFoundException;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Menyimpan berkas di folder pada mesin lokal. Folder sengaja berada DI LUAR
 * repo agar tidak ikut ter-commit dan tidak hilang saat `mvn clean`.
 */
@Service
@Slf4j
public class LocalFileStorage implements FileStorage {

    public static final String PROVIDER = "LOCAL";

    /**
     * Penanda berkas contoh: barisnya ada di dataset_resource lengkap dengan
     * nama, jenis, dan ukuran, tetapi isinya sengaja tidak disertakan ke dalam
     * repo. Lihat changeset 00028.
     */
    public static final String SEED_PROVIDER = "SEED";

    @Value("${satudata.storage.local.base-path:${user.home}/.satudata/files}")
    private String basePath;

    private Path root;

    @PostConstruct
    public void init() throws IOException {
        this.root = Paths.get(basePath).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("Penyimpanan berkas lokal aktif di {}", root);
    }

    @Override
    public String getProviderName() {
        return PROVIDER;
    }

    @Override
    public StoredFile store(InputStream in, String storageKey, String contentType) {
        Path target = resolve(storageKey);
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream dis = new DigestInputStream(in, digest)) {
                Files.copy(dis, target, StandardCopyOption.REPLACE_EXISTING);
            }
            long size = Files.size(target);
            String checksum = HexFormat.of().formatHex(digest.digest());
            return new StoredFile(PROVIDER, storageKey, size, checksum);
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException("Gagal menyimpan berkas: " + storageKey, e);
        }
    }

    @Override
    public InputStream open(String storageKey) {
        Path target = resolve(storageKey);
        if (!Files.exists(target)) {
            throw new ResourceNotFoundException("Berkas tidak ditemukan: " + storageKey);
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new IllegalStateException("Gagal membuka berkas: " + storageKey, e);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.exists(resolve(storageKey));
    }

    @Override
    public boolean isHealthy() {
        return root != null && Files.isDirectory(root) && Files.isWritable(root);
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            throw new IllegalStateException("Gagal menghapus berkas: " + storageKey, e);
        }
    }

    /**
     * Menahan path traversal — storageKey berasal dari database, tapi tetap
     * tidak boleh bisa keluar dari folder root.
     */
    private Path resolve(String storageKey) {
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Storage key tidak sah: " + storageKey);
        }
        return target;
    }

    /** Dipakai importer untuk menulis dari berkas lain tanpa memuat ke memori. */
    public StoredFile storeFrom(Path source, String storageKey, String contentType) {
        try (InputStream in = Files.newInputStream(source)) {
            return store(in, storageKey, contentType);
        } catch (IOException e) {
            throw new IllegalStateException("Gagal membaca sumber: " + source, e);
        }
    }
}
