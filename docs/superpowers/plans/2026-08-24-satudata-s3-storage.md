# Penyimpanan Berkas Dataset di AWS S3 — Rencana Implementasi

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Berkas dataset satudata tersimpan di bucket S3 `erhanesia-files`, bukan di disk mesin yang menjalankan aplikasi.

**Architecture:** Antarmuka `FileStorage` sudah ada dan sudah dipakai `DownloadService` serta `StatusService`. Pekerjaan ini menambah implementasi kedua, `S3FileStorage`, dan memilih salah satu implementasi lewat properti `satudata.storage.provider`. Byte tetap mengalir lewat aplikasi supaya `DownloadLog` tetap berarti "berkas benar-benar terunduh". Satu kebocoran abstraksi di `DatasetImportService` ditutup di tugas terakhir.

**Tech Stack:** Java 17, Spring Boot 4.1.0, Maven, AWS SDK for Java v2 (`software.amazon.awssdk:s3`), JUnit 5 + AssertJ.

**Spec:** `docs/superpowers/specs/2026-08-24-satudata-s3-storage-design.md`

## Global Constraints

- Repo: `d:\Erdigma\satudata-erdigma-api`. Semua path di bawah relatif terhadap repo itu.
- Versi AWS SDK **persis** `2.29.0` — sama dengan `hris-api/pom.xml`.
- Bucket `erhanesia-files`, region `ap-southeast-1`.
- **Tidak boleh ada properti kredensial AWS di berkas mana pun.** Klien memakai `DefaultCredentialsProvider` bawaan SDK. Menyalin pola `aws.accessKeyId`/`aws.secretAccessKey` dari hris-api adalah kegagalan tugas: di sana nilainya plaintext dan sudah ter-commit.
- Konstanta provider: `LocalFileStorage.PROVIDER = "LOCAL"`, `S3FileStorage.PROVIDER = "S3"`. Nilai ini masuk kolom `dataset_resource.storage_provider`; jangan diubah.
- `storageKey` yang disimpan ke database tetap **relatif** (`dataset/{slug}/{namaBerkas}`). Prefix `satudata/{stage}/` hanya hidup di dalam `S3FileStorage`.
- Pesan galat dan komentar ditulis dalam bahasa Indonesia, mengikuti gaya `LocalFileStorage`.
- Jangan sentuh perubahan lokal yang belum di-commit di `application-dev.yaml` dan `application.yaml` (`DB_PASSWORD`, `show-sql`, `hris.base-url`). Itu setelan mesin, bukan bagian pekerjaan ini.
- Perintah build: `./mvnw` dari akar repo (Git Bash) atau `.\mvnw.cmd` (PowerShell).
- Tes `@SpringBootTest` yang sudah ada menuntut PostgreSQL hidup di `localhost:5432` database `satudata`. Nyalakan dulu sebelum menjalankan `./mvnw test`.

---

### Task 1: Dependensi, klien S3, dan pemilihan implementasi

Tugas ini belum membuat `S3FileStorage`. Yang dikerjakan: menyiapkan dependensi, bean klien, properti konfigurasi, dan sakelar pemilih — dengan bawaan masih `LOCAL` supaya aplikasi tetap bisa start di akhir tugas. Tugas 3 yang membalik bawaan ke `S3`.

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/id/co/erdigma/satudata/config/S3Config.java`
- Modify: `src/main/resources/application.yaml`
- Modify: `src/main/java/id/co/erdigma/satudata/service/storage/LocalFileStorage.java`
- Modify: `src/test/java/id/co/erdigma/satudata/SatudataApplicationTests.java`

**Interfaces:**
- Consumes: tidak ada.
- Produces: bean `S3Client s3Client(String region)` untuk Tugas 2. Properti `satudata.storage.provider` (nilai `LOCAL` atau `S3`), `satudata.storage.s3.bucket`, `satudata.storage.s3.region`.

- [ ] **Step 1: Tambah dependensi AWS SDK**

Di `pom.xml`, sisipkan blok berikut di dalam `<dependencies>`, tepat sesudah dependensi `postgresql`:

```xml
		<dependency>
			<groupId>software.amazon.awssdk</groupId>
			<artifactId>s3</artifactId>
			<version>2.29.0</version>
		</dependency>
```

- [ ] **Step 2: Pastikan dependensi terunduh**

Run: `./mvnw -q dependency:resolve`
Expected: selesai tanpa galat. Artefak sudah ada di cache lokal (`~/.m2/repository/software/amazon/awssdk/s3/2.29.0`), jadi tidak butuh jaringan.

- [ ] **Step 3: Buat bean klien S3**

Buat `src/main/java/id/co/erdigma/satudata/config/S3Config.java`:

```java
package id.co.erdigma.satudata.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Klien S3 sengaja dibangun TANPA properti kredensial apa pun.
 * DefaultCredentialsProvider bawaan SDK menyelesaikannya berurutan: variabel
 * lingkungan AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY, lalu ~/.aws/credentials,
 * lalu IAM role instance.
 *
 * Akibatnya laptop yang sudah `aws configure`, penyebaran dengan variabel
 * lingkungan, dan perpindahan ke EC2/ECS nanti sama-sama jalan tanpa perubahan
 * kode — dan tidak ada rahasia yang bisa ikut ter-commit. Ini berbeda dari
 * hris-api, yang menaruh access key plaintext di application.properties.
 */
@Configuration
@ConditionalOnProperty(name = "satudata.storage.provider", havingValue = "S3")
public class S3Config {

    @Bean
    public S3Client s3Client(@Value("${satudata.storage.s3.region}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }
}
```

- [ ] **Step 4: Tambah properti penyimpanan**

Di `src/main/resources/application.yaml`, di dalam blok `satudata:` yang sudah ada (sejajar dengan `api-key:`), tambahkan:

```yaml
  storage:
    # LOCAL atau S3. Dibalik ke S3 pada Tugas 3, setelah S3FileStorage ada.
    provider: ${STORAGE_PROVIDER:LOCAL}
    s3:
      # Bucket yang sama dengan hris-api. Kunci dipisah prefix satudata/{stage}/
      # sehingga dev dan prod tidak saling timpa.
      bucket: ${AWS_S3_BUCKET:erhanesia-files}
      region: ${AWS_REGION:ap-southeast-1}
```

- [ ] **Step 5: Jadikan LocalFileStorage bersyarat**

Di `src/main/java/id/co/erdigma/satudata/service/storage/LocalFileStorage.java`, tambahkan import:

```java
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
```

lalu ubah anotasi kelas dari:

```java
@Service
@Slf4j
public class LocalFileStorage implements FileStorage {
```

menjadi:

```java
@Service
@ConditionalOnProperty(name = "satudata.storage.provider", havingValue = "LOCAL")
@Slf4j
public class LocalFileStorage implements FileStorage {
```

Tanpa syarat ini, begitu `S3FileStorage` lahir di Tugas 2 akan ada dua kandidat bean `FileStorage` dan aplikasi menolak start.

- [ ] **Step 6: Pin provider pada tes konteks**

Ganti isi `src/test/java/id/co/erdigma/satudata/SatudataApplicationTests.java` menjadi:

```java
package id.co.erdigma.satudata;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Provider dipin LOCAL supaya pemuatan konteks tidak pernah menyentuh AWS —
 * tidak butuh kredensial, tidak butuh jaringan, dan tidak melambat.
 */
@SpringBootTest(properties = "satudata.storage.provider=LOCAL")
class SatudataApplicationTests {

	@Test
	void contextLoads() {
	}

}
```

- [ ] **Step 7: Jalankan tes**

Run: `./mvnw test -Dtest=SatudataApplicationTests`
Expected: `BUILD SUCCESS`, `Tests run: 1, Failures: 0, Errors: 0`.
Kalau gagal dengan `Connection to localhost:5432 refused`, nyalakan PostgreSQL lokal dulu — itu bukan galat pekerjaan ini.

- [ ] **Step 8: Verifikasi sakelar benar-benar memilih**

Run: `./mvnw test -Dtest=SatudataApplicationTests -Dspring-boot.run.arguments=--satudata.storage.provider=S3` — atau lebih mudah, ubah sementara `@SpringBootTest(properties = "satudata.storage.provider=S3")` lalu jalankan `./mvnw test -Dtest=SatudataApplicationTests`.
Expected: **GAGAL** dengan pesan yang menyebut tidak ada kandidat bean `FileStorage` (`UnsatisfiedDependencyException` pada `DownloadService` atau `NoSuchBeanDefinitionException`).
Ini hasil yang benar dan disengaja: `S3FileStorage` memang belum ada, dan kegagalan ini membuktikan `@ConditionalOnProperty` pada `LocalFileStorage` bekerja. **Kembalikan nilainya ke `LOCAL`** sebelum lanjut.

- [ ] **Step 9: Commit**

```bash
git add pom.xml src/main/java/id/co/erdigma/satudata/config/S3Config.java src/main/resources/application.yaml src/main/java/id/co/erdigma/satudata/service/storage/LocalFileStorage.java src/test/java/id/co/erdigma/satudata/SatudataApplicationTests.java
git commit -m "feat(storage): dependensi AWS SDK, bean klien S3, dan sakelar pemilih penyimpanan"
```

---

### Task 2: Implementasi `S3FileStorage`

**Files:**
- Create: `src/main/java/id/co/erdigma/satudata/service/storage/S3FileStorage.java`
- Test: `src/test/java/id/co/erdigma/satudata/service/storage/S3FileStorageTest.java`

**Interfaces:**
- Consumes: bean `S3Client` dari Tugas 1; properti `satudata.storage.s3.bucket` dan `custom.stage`.
- Produces:
  - `public class S3FileStorage implements FileStorage`
  - `public static final String PROVIDER = "S3"`
  - Konstruktor publik `S3FileStorage(S3Client s3, String bucket, String stage)` — dipakai langsung oleh tes tanpa Spring.
  - `public StoredFile storeFrom(Path source, String storageKey, String contentType)` — di Tugas 3 metode ini menjadi override dari anggota antarmuka.

Tes memakai kelas palsu buatan tangan, bukan Mockito. Seluruh metode operasi pada antarmuka `S3Client` punya implementasi `default`, jadi kelas palsu cukup mengisi `serviceName()`, `close()`, dan operasi yang benar-benar dipakai. Ini menghindari pertanyaan apakah Mockito ada di classpath tes Spring Boot 4 yang modular.

- [ ] **Step 1: Tulis tes yang gagal**

Buat `src/test/java/id/co/erdigma/satudata/service/storage/S3FileStorageTest.java`:

```java
package id.co.erdigma.satudata.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import id.co.erdigma.satudata.exception.ResourceNotFoundException;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

class S3FileStorageTest {

    private static final String BUCKET = "erhanesia-files";

    /**
     * Seluruh operasi pada antarmuka S3Client punya implementasi default, jadi
     * kelas palsu ini cukup mengisi yang benar-benar dipakai. Tidak ada
     * jaringan, tidak ada kredensial, tidak ada Mockito.
     */
    private static class FakeS3Client implements S3Client {

        final List<PutObjectRequest> puts = new ArrayList<>();
        boolean getMelemparNoSuchKey = false;

        @Override
        public String serviceName() {
            return S3Client.SERVICE_NAME;
        }

        @Override
        public void close() {
        }

        @Override
        public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
            puts.add(request);
            return PutObjectResponse.builder().build();
        }

        @Override
        public ResponseInputStream<GetObjectResponse> getObject(GetObjectRequest request) {
            if (getMelemparNoSuchKey) {
                throw NoSuchKeyException.builder()
                        .message("The specified key does not exist.")
                        .build();
            }
            throw new UnsupportedOperationException("Tidak dipakai tes ini");
        }
    }

    @Test
    @DisplayName("Kunci yang dikirim ke S3 memuat prefix satudata/{stage}/")
    void kunciMemuatPrefixStage() {
        FakeS3Client fake = new FakeS3Client();
        S3FileStorage storage = new S3FileStorage(fake, BUCKET, "dev");

        storage.store(new ByteArrayInputStream("a,b\n1,2\n".getBytes(StandardCharsets.UTF_8)),
                "dataset/contoh/contoh.csv", "text/csv");

        assertThat(fake.puts).hasSize(1);
        assertThat(fake.puts.get(0).key()).isEqualTo("satudata/dev/dataset/contoh/contoh.csv");
        assertThat(fake.puts.get(0).bucket()).isEqualTo(BUCKET);
        assertThat(fake.puts.get(0).contentType()).isEqualTo("text/csv");
    }

    @Test
    @DisplayName("Prefix ikut stage, bukan dipatok dev")
    void prefixIkutStage() {
        FakeS3Client fake = new FakeS3Client();
        S3FileStorage storage = new S3FileStorage(fake, BUCKET, "prod");

        storage.store(new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)),
                "dataset/x/x.csv", "text/csv");

        assertThat(fake.puts.get(0).key()).isEqualTo("satudata/prod/dataset/x/x.csv");
    }

    @Test
    @DisplayName("Ukuran dan SHA-256 dihitung dari isi yang diunggah")
    void ukuranDanChecksumDihitung() {
        FakeS3Client fake = new FakeS3Client();
        S3FileStorage storage = new S3FileStorage(fake, BUCKET, "dev");

        StoredFile stored = storage.store(
                new ByteArrayInputStream("halo".getBytes(StandardCharsets.UTF_8)),
                "dataset/x/x.csv", "text/csv");

        assertThat(stored.getStorageProvider()).isEqualTo("S3");
        // Kunci yang dikembalikan tetap relatif — prefix tidak ikut ke database.
        assertThat(stored.getStorageKey()).isEqualTo("dataset/x/x.csv");
        assertThat(stored.getSizeBytes()).isEqualTo(4L);
        assertThat(stored.getChecksumSha256())
                .isEqualTo("a4e63bcacf6c172ad84f9f4523c8f1acaf33676fa76d3258c67b7e7bbf16d777");
    }

    @Test
    @DisplayName("Kunci yang tidak ada di S3 muncul sebagai ResourceNotFoundException")
    void kunciHilangJadiResourceNotFound() {
        FakeS3Client fake = new FakeS3Client();
        fake.getMelemparNoSuchKey = true;
        S3FileStorage storage = new S3FileStorage(fake, BUCKET, "dev");

        assertThatThrownBy(() -> storage.open("dataset/x/x.csv"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("dataset/x/x.csv");
    }

    @Test
    @DisplayName("Storage key yang mencoba keluar dari prefix ditolak")
    void kunciTraversalDitolak() {
        S3FileStorage storage = new S3FileStorage(new FakeS3Client(), BUCKET, "dev");

        assertThatThrownBy(() -> storage.open("../../rahasia/kunci.csv"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.open("/dataset/x/x.csv"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Jalankan tes untuk memastikan gagal**

Run: `./mvnw test -Dtest=S3FileStorageTest`
Expected: **GAGAL saat kompilasi** dengan `cannot find symbol: class S3FileStorage`.

- [ ] **Step 3: Tulis implementasi**

Buat `src/main/java/id/co/erdigma/satudata/service/storage/S3FileStorage.java`:

```java
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
            return s3.getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(fullKey)
                    .build());
        } catch (NoSuchKeyException e) {
            throw new ResourceNotFoundException("Berkas tidak ditemukan: " + storageKey);
        } catch (SdkException e) {
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
```

- [ ] **Step 4: Jalankan tes untuk memastikan lulus**

Run: `./mvnw test -Dtest=S3FileStorageTest`
Expected: `Tests run: 5, Failures: 0, Errors: 0` — `BUILD SUCCESS`.

- [ ] **Step 5: Pastikan tes lain tidak rusak**

Run: `./mvnw test`
Expected: `BUILD SUCCESS`. `SatudataApplicationTests` tetap lulus karena provider dipin `LOCAL`, jadi `S3FileStorage` tidak dibuat dan `laporkanKesiapan()` tidak pernah menyentuh jaringan.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/id/co/erdigma/satudata/service/storage/S3FileStorage.java src/test/java/id/co/erdigma/satudata/service/storage/S3FileStorageTest.java
git commit -m "feat(storage): implementasi S3FileStorage dengan prefix per stage"
```

---

### Task 3: Tutup kebocoran seam dan aktifkan S3

**Files:**
- Modify: `src/main/java/id/co/erdigma/satudata/service/storage/FileStorage.java`
- Modify: `src/main/java/id/co/erdigma/satudata/service/storage/LocalFileStorage.java`
- Modify: `src/main/java/id/co/erdigma/satudata/service/storage/S3FileStorage.java`
- Modify: `src/main/java/id/co/erdigma/satudata/modules/dataset/service/DatasetImportService.java` (baris 32, 89, 232)
- Modify: `src/main/resources/application.yaml`
- Modify: `src/main/resources/application-prod.yaml`

**Interfaces:**
- Consumes: `S3FileStorage` dan `S3FileStorage.storeFrom(Path, String, String)` dari Tugas 2.
- Produces: `FileStorage.storeFrom(Path source, String storageKey, String contentType)` sebagai anggota antarmuka dengan implementasi `default`.

`DatasetImportService` saat ini meng-inject `LocalFileStorage` konkret. Kalau dibiarkan, impor dataset diam-diam menulis ke disk lokal sementara `DownloadService` membaca dari S3 — berkas terdaftar di database tapi tidak pernah bisa diunduh dari instance lain. Ini satu-satunya pemanggil `storeFrom`, jadi memindahkan metode itu ke antarmuka memperbaiki akarnya sekali.

- [ ] **Step 1: Naikkan `storeFrom` ke antarmuka**

Di `src/main/java/id/co/erdigma/satudata/service/storage/FileStorage.java`, ganti baris import:

```java
import java.io.InputStream;
```

menjadi:

```java
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
```

lalu tambahkan metode berikut tepat sesudah `StoredFile store(InputStream in, String storageKey, String contentType);`:

```java
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
```

- [ ] **Step 2: Hapus `storeFrom` duplikat di LocalFileStorage**

Di `src/main/java/id/co/erdigma/satudata/service/storage/LocalFileStorage.java`, hapus seluruh metode berikut beserta komentarnya — implementasi `default` di antarmuka sudah identik:

```java
    /** Dipakai importer untuk menulis dari berkas lain tanpa memuat ke memori. */
    public StoredFile storeFrom(Path source, String storageKey, String contentType) {
        try (InputStream in = Files.newInputStream(source)) {
            return store(in, storageKey, contentType);
        } catch (IOException e) {
            throw new IllegalStateException("Gagal membaca sumber: " + source, e);
        }
    }
```

Import `java.nio.file.Path` di berkas itu tetap dipakai metode `resolve`, jadi jangan ikut dihapus.

- [ ] **Step 3: Tandai override di S3FileStorage**

Di `src/main/java/id/co/erdigma/satudata/service/storage/S3FileStorage.java`, tambahkan `@Override` pada `storeFrom`:

```java
    /** Mengunggah langsung dari berkas — importer sudah punya Path, tak perlu disalin ulang. */
    @Override
    public StoredFile storeFrom(Path source, String storageKey, String contentType) {
```

- [ ] **Step 4: Alihkan DatasetImportService ke antarmuka**

Di `src/main/java/id/co/erdigma/satudata/modules/dataset/service/DatasetImportService.java`:

Baris 32 — ganti import:

```java
import id.co.erdigma.satudata.service.storage.LocalFileStorage;
```

menjadi:

```java
import id.co.erdigma.satudata.service.storage.FileStorage;
```

Baris 89 — ganti deklarasi field:

```java
    private LocalFileStorage localFileStorage;
```

menjadi:

```java
    private FileStorage fileStorage;
```

Baris 232 — ganti pemanggilan:

```java
        StoredFile stored = localFileStorage.storeFrom(source, storageKey, contentType);
```

menjadi:

```java
        StoredFile stored = fileStorage.storeFrom(source, storageKey, contentType);
```

- [ ] **Step 5: Kompilasi dan jalankan seluruh tes**

Run: `./mvnw test`
Expected: `BUILD SUCCESS`.

Lalu pastikan tidak ada rujukan konkret yang tertinggal:

Run: `grep -rn "localFileStorage\|LocalFileStorage" src/main/java --include=*.java`
Expected: hanya berkas `LocalFileStorage.java` sendiri yang muncul.

- [ ] **Step 6: Balikkan bawaan provider ke S3**

Di `src/main/resources/application.yaml`, ganti dua baris:

```yaml
    # LOCAL atau S3. Dibalik ke S3 pada Tugas 3, setelah S3FileStorage ada.
    provider: ${STORAGE_PROVIDER:LOCAL}
```

menjadi:

```yaml
    # LOCAL atau S3. Setel STORAGE_PROVIDER=LOCAL untuk bekerja tanpa jaringan.
    provider: ${STORAGE_PROVIDER:S3}
```

- [ ] **Step 7: Pin stage di profil prod**

`custom.stage` bawaannya `${STAGE:dev}`. Kalau produksi lupa menyetel `STAGE`, seluruh berkas produksi mendarat di prefix `satudata/dev/` dan bercampur dengan data percobaan. Di `src/main/resources/application-prod.yaml`, tambahkan di akhir berkas:

```yaml
# Dipin di sini, tidak diwariskan dari application.yaml. Nilai ini menentukan
# prefix kunci S3 (satudata/{stage}/); satu variabel lingkungan yang lupa
# di-set akan membuat berkas produksi mendarat di prefix dev.
custom:
  stage: prod
```

- [ ] **Step 8: Jalankan seluruh tes lagi**

Run: `./mvnw test`
Expected: `BUILD SUCCESS`. `SatudataApplicationTests` tetap lulus karena memin `satudata.storage.provider=LOCAL` secara eksplisit, bukan mengandalkan bawaan.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/id/co/erdigma/satudata/service/storage/FileStorage.java src/main/java/id/co/erdigma/satudata/service/storage/LocalFileStorage.java src/main/java/id/co/erdigma/satudata/service/storage/S3FileStorage.java src/main/java/id/co/erdigma/satudata/modules/dataset/service/DatasetImportService.java src/main/resources/application.yaml src/main/resources/application-prod.yaml
git commit -m "feat(storage): aktifkan S3 sebagai bawaan dan tutup kebocoran seam di importer"
```

---

### Task 4: Verifikasi ujung-ke-ujung dengan AWS sungguhan

Tugas ini tidak mengubah kode. Tugas ini membuktikan integrasinya benar-benar jalan — tes unit memakai klien palsu, jadi sampai titik ini belum ada satu pun byte yang pernah sampai ke S3.

**Files:** tidak ada.

**Interfaces:**
- Consumes: seluruh hasil Tugas 1–3.
- Produces: tidak ada.

**Prasyarat:** kredensial AWS tersedia — entah lewat `aws configure` atau lewat variabel lingkungan `AWS_ACCESS_KEY_ID` dan `AWS_SECRET_ACCESS_KEY`. Kredensial tidak boleh ditulis ke berkas mana pun di dalam repo ini.

- [ ] **Step 1: Pastikan kredensial terbaca**

Run: `aws sts get-caller-identity`
Expected: JSON berisi `Account`, `Arn`, `UserId`. Kalau gagal, benahi kredensial dulu sebelum lanjut.

- [ ] **Step 2: Jalankan aplikasi**

Run: `./mvnw spring-boot:run`
Expected: di log muncul baris

```
Penyimpanan S3 aktif di bucket erhanesia-files dengan prefix satudata/dev/
```

Kalau yang muncul `Bucket S3 erhanesia-files tidak terjangkau`, kredensial tidak punya izin pada bucket itu. Jangan menaruh kunci di berkas repo untuk mengakalinya.

- [ ] **Step 3: Periksa halaman status**

Run: `curl -s http://localhost:8082/api/v1/status`
Expected: komponen `Datastore & unduhan` bernilai `OPERATIONAL`, bukan `DEGRADED`.

- [ ] **Step 4: Unggah satu dataset CSV**

Lewat Swagger UI di `http://localhost:8082/swagger-ui.html`, panggil endpoint unggah dataset pada tag Datasets dengan satu berkas CSV kecil.
Expected: respons sukses, dan dataset baru muncul di katalog.

- [ ] **Step 5: Pastikan objeknya benar-benar ada di S3**

Run: `aws s3 ls s3://erhanesia-files/satudata/dev/dataset/ --recursive`
Expected: berkas yang barusan diunggah terdaftar, dengan kunci berawalan `satudata/dev/dataset/`.

- [ ] **Step 6: Unduh kembali lewat aplikasi**

Panggil endpoint unduh dataset untuk slug yang barusan dibuat.
Expected: isi berkas identik dengan yang diunggah.

Run: `psql -h localhost -U postgres -d satudata -c "SELECT file_name, size_bytes, created_at FROM download_log ORDER BY created_at DESC LIMIT 1;"`
Expected: satu baris berisi nama berkas yang barusan diunduh — bukti bahwa byte melewati aplikasi dan audit tercatat.

- [ ] **Step 7: Laporkan hasilnya**

Kalau seluruh langkah lulus, integrasi selesai. Kalau ada yang gagal, laporkan langkah mana dan pesan galat persisnya sebelum mengubah kode apa pun.
