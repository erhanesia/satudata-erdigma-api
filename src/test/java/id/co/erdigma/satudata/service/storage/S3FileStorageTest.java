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
