package id.co.erdigma.satudata.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import id.co.erdigma.satudata.exception.ResourceNotFoundException;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Menyimpan berkas di bucket AWS S3.
 *
 * Bucket dipakai bersama aplikasi lain, jadi seluruh kunci diberi awalan
 * "satudata/{stage}/". Awalan itu TIDAK ikut disimpan ke database: kolom
 * dataset_resource.storage_key tetap relatif, sehingga pindah bucket atau ganti
 * skema awalan tidak menuntut migrasi data.
 *
 * Byte unduhan tetap mengalir lewat aplikasi, bukan presigned URL — lihat
 * alasannya di komentar {@link FileStorage}.
 */
@Service
@ConditionalOnProperty(name = "satudata.storage.provider", havingValue = "S3")
@Slf4j
public class S3FileStorage implements FileStorage {

    public static final String PROVIDER = "S3";

    private final S3Client s3;
    private final String bucket;
    private final String keyPrefix;

    public S3FileStorage(S3Client s3,
            @Value("${satudata.storage.s3.bucket}") String bucket,
            @Value("${custom.stage}") String stage) {
        this.s3 = s3;
        this.bucket = bucket;
        this.keyPrefix = "satudata/" + stage + "/";
    }

    /**
     * Melaporkan kesiapan sekali saat start, TANPA mematikan aplikasi bila
     * gagal. Gagal-diam pada unggah lebih mahal daripada start yang gagal, dan
     * halaman Status Produk sudah menjadi tempat yang benar untuk melaporkannya.
     */
    @PostConstruct
    void laporkanKesiapan() {
        if (isHealthy()) {
            log.info("Penyimpanan S3 aktif di bucket {} dengan prefix {}", bucket, keyPrefix);
        } else {
            log.warn("Bucket S3 {} tidak terjangkau. Unggah dan unduh berkas akan gagal "
                    + "sampai kredensial AWS tersedia.", bucket);
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER;
    }

    @Override
    public StoredFile store(InputStream in, String storageKey, String contentType) {
        String fullKey = fullKey(storageKey);
        Path temp = null;
        try {
            // putObject menuntut content-length, yang tidak diketahui dari
            // InputStream. Salin ke berkas sementara sambil menghitung ukuran
            // dan sidik jari dalam satu lintasan.
            temp = Files.createTempFile("satudata-", ".upload");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size;
            try (DigestInputStream dis = new DigestInputStream(in, digest)) {
                size = Files.copy(dis, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            putObject(temp, fullKey, contentType);
            return new StoredFile(PROVIDER, storageKey, size,
                    HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException("Gagal menyimpan berkas: " + storageKey, e);
        } finally {
            hapusDiam(temp);
        }
    }

    /** Mengunggah langsung dari berkas — importer sudah punya Path, tak perlu disalin ulang. */
    @Override
    public StoredFile storeFrom(Path source, String storageKey, String contentType) {
        String fullKey = fullKey(storageKey);
        try {
            long size = Files.size(source);
            String checksum = sha256(source);
            putObject(source, fullKey, contentType);
            return new StoredFile(PROVIDER, storageKey, size, checksum);
        } catch (IOException e) {
            throw new IllegalStateException("Gagal membaca sumber: " + source, e);
        }
    }

    @Override
    public InputStream open(String storageKey) {
        String fullKey = fullKey(storageKey);
        try {
            return GzipStorage.decompressIfNeeded(storageKey,
                    s3.getObject(GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(fullKey)
                            .build()));
        } catch (NoSuchKeyException e) {
            throw new ResourceNotFoundException("Berkas tidak ditemukan: " + storageKey);
        } catch (SdkException | IOException e) {
            throw new IllegalStateException("Gagal membuka berkas: " + storageKey, e);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        try {
            s3.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(fullKey(storageKey))
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (SdkException e) {
            log.warn("Gagal memeriksa keberadaan {}: {}", storageKey, e.getMessage());
            return false;
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(fullKey(storageKey))
                    .build());
        } catch (SdkException e) {
            throw new IllegalStateException("Gagal menghapus berkas: " + storageKey, e);
        }
    }

    @Override
    public boolean isHealthy() {
        try {
            // ponytail: satu headBucket per kunjungan halaman Status Produk.
            // Bungkus dengan Caffeine (sudah jadi dependensi) kalau halaman itu
            // ramai dikunjungi.
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return true;
        } catch (SdkException e) {
            log.debug("headBucket {} gagal: {}", bucket, e.getMessage());
            return false;
        }
    }

    private void putObject(Path source, String fullKey, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(fullKey)
                .contentType(contentType)
                .build();
        try {
            s3.putObject(request, source);
        } catch (SdkException e) {
            throw new IllegalStateException("Gagal mengunggah berkas ke S3: " + fullKey, e);
        }
    }

    /**
     * Kunci S3 memang tidak bisa menembus keluar bucket, tetapi awalan
     * satudata/{stage}/ harus tetap mengikat — tanpa penjagaan ini sebuah kunci
     * berisi ".." bisa menunjuk objek milik aplikasi lain di bucket yang sama.
     */
    private String fullKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()
                || storageKey.startsWith("/") || storageKey.contains("..")) {
            throw new IllegalArgumentException("Storage key tidak sah: " + storageKey);
        }
        return keyPrefix + storageKey;
    }

    private String sha256(Path source) throws IOException {
        try (InputStream in = Files.newInputStream(source)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 tidak tersedia", e);
        }
    }

    private void hapusDiam(Path temp) {
        if (temp == null) {
            return;
        }
        try {
            Files.deleteIfExists(temp);
        } catch (IOException e) {
            log.warn("Berkas sementara {} gagal dihapus: {}", temp, e.getMessage());
        }
    }
}
