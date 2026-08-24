package id.co.erdigma.satudata.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import software.amazon.awssdk.services.s3.model.S3Exception;

class S3FileStorageTest {

    private static final String BUCKET = "erhanesia-files";

    /**
     * Seluruh operasi pada antarmuka S3Client punya implementasi default, jadi
     * kelas palsu ini cukup mengisi yang benar-benar dipakai. Tidak ada
     * jaringan, tidak ada kredensial, tidak ada Mockito.
     */
    private static class FakeS3Client implements S3Client {

        final List<PutObjectRequest> puts = new ArrayList<>();
        final List<byte[]> payloads = new ArrayList<>();
        boolean getMelemparNoSuchKey = false;
        boolean putMelempar = false;

        @Override
        public String serviceName() {
            return S3Client.SERVICE_NAME;
        }

        @Override
        public void close() {
        }

        @Override
        public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
            if (putMelempar) {
                throw S3Exception.builder().message("Simulasi galat S3 saat unggah").build();
            }
            puts.add(request);
            try (InputStream is = body.contentStreamProvider().newStream()) {
                payloads.add(is.readAllBytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
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

    @Test
    @DisplayName("Muatan yang benar-benar diunggah ke S3 sama dengan isi yang dihitung sidik jarinya")
    void muatanYangDiunggahSamaDenganIsiAsli() {
        FakeS3Client fake = new FakeS3Client();
        S3FileStorage storage = new S3FileStorage(fake, BUCKET, "dev");
        byte[] isi = "isi berkas yang diunggah ke S3".getBytes(StandardCharsets.UTF_8);

        storage.store(new ByteArrayInputStream(isi), "dataset/x/x.csv", "text/csv");

        assertThat(fake.payloads).hasSize(1);
        assertThat(fake.payloads.get(0)).isEqualTo(isi);
    }

    @Test
    @DisplayName("Galat S3 lain saat unggah menjadi IllegalStateException berpesan Indonesia")
    void galatUnggahLainJadiIllegalStateException() {
        FakeS3Client fake = new FakeS3Client();
        fake.putMelempar = true;
        S3FileStorage storage = new S3FileStorage(fake, BUCKET, "dev");

        assertThatThrownBy(() -> storage.store(
                new ByteArrayInputStream("halo".getBytes(StandardCharsets.UTF_8)),
                "dataset/x/x.csv", "text/csv"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Gagal mengunggah berkas ke S3: satudata/dev/dataset/x/x.csv");
    }

    @Test
    @DisplayName("Berkas sementara di direktori tmp dibersihkan meski unggahan gagal")
    void berkasSementaraDibersihkanSaatUnggahGagal() throws IOException {
        FakeS3Client fake = new FakeS3Client();
        fake.putMelempar = true;
        S3FileStorage storage = new S3FileStorage(fake, BUCKET, "dev");

        Set<Path> sebelum = daftarBerkasSementaraSatudata();

        assertThatThrownBy(() -> storage.store(
                new ByteArrayInputStream("halo".getBytes(StandardCharsets.UTF_8)),
                "dataset/x/x.csv", "text/csv"))
                .isInstanceOf(IllegalStateException.class);

        Set<Path> berkasBaruYangTersisa = daftarBerkasSementaraSatudata();
        berkasBaruYangTersisa.removeAll(sebelum);

        assertThat(berkasBaruYangTersisa).isEmpty();
    }

    /**
     * Hanya mencocokkan pola nama berkas sementara milik S3FileStorage
     * (satudata-*.upload) di java.io.tmpdir, supaya tes ini tidak goyah gara-gara
     * proses lain menulis ke direktori tmp yang sama.
     */
    private Set<Path> daftarBerkasSementaraSatudata() throws IOException {
        Path tmpDir = Path.of(System.getProperty("java.io.tmpdir"));
        Set<Path> hasil = new HashSet<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tmpDir, "satudata-*.upload")) {
            stream.forEach(hasil::add);
        }
        return hasil;
    }
}
