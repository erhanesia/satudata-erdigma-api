package id.co.erdigma.satudata.modules.dataset.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.HrisPermissionLevel;
import id.co.erdigma.satudata.enums.Role;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetRequestCreateDTO;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetResponse;
import id.co.erdigma.satudata.modules.division.repository.DivisionRepository;
import id.co.erdigma.satudata.repository.UserRepository;

/**
 * Ukuran dataset BARU dihitung dari berkas yang tersimpan, bukan dari byte
 * yang terkirim.
 *
 * <h2>Cacat yang dijaga</h2>
 *
 * Peramban mengompresi CSV sebelum mengirimnya. Jalur buat dataset dulu
 * mengisi {@code fileSize} dari byte terkirim, sehingga CSV 16,5 MB tampil
 * "Unduh · 7,7 MB" di halaman detail, sementara modal unduh dan berkas yang
 * diterima pengunduh 16,5 MB. Jalur sunting sudah benar sejak awal, jadi
 * cacatnya hanya terlihat pada dataset yang belum pernah disunting.
 *
 * <h2>Kenapa aman dijalankan</h2>
 *
 * Seluruh kelas berada di dalam transaksi yang selalu dibatalkan, termasuk
 * pengguna buatannya, dan penyimpanan berkas dipin LOCAL sehingga tidak
 * menyentuh AWS.
 */
@SpringBootTest(properties = "satudata.storage.provider=LOCAL")
@Transactional
class DatasetUploadSizePersistenceTest {

    /**
     * Sengaja sangat berulang, supaya ukuran terkirim dan ukuran aslinya tampil
     * sebagai angka yang jelas berbeda: sekitar 40 KB menyusut menjadi ratusan
     * byte.
     */
    private static final int VALUE_LENGTH = 500;
    private static final int ROW_COUNT = 80;

    @Autowired
    private DatasetUploadService datasetUploadService;
    @Autowired
    private DatasetFileService datasetFileService;
    @Autowired
    private DivisionRepository divisionRepository;
    @Autowired
    private UserRepository userRepository;

    private User publisher;

    @BeforeEach
    void seed() {
        String cognitoId = "it-test-" + UUID.randomUUID();

        publisher = new User();
        publisher.setCognitoId(cognitoId);
        publisher.setName("Penerbit Uji Ukuran");
        publisher.setEmail(cognitoId + "@erdigma.co.id");
        publisher.setRole(Role.PUBLISHER);
        publisher.setHrisPermissionLevel(HrisPermissionLevel.MANAGER);
        publisher.setDivision(divisionRepository.findAll().stream()
                .filter(d -> d.getDeletedAt() == null)
                .findFirst()
                .orElseThrow());
        publisher.setUpdatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        userRepository.save(publisher);
    }

    private static byte[] originalCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("kolom_a,kolom_b\n");
        for (int i = 1; i <= ROW_COUNT; i++) {
            sb.append(i).append(',').append("x".repeat(VALUE_LENGTH)).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Meniru kiriman peramban: byte gzip, nama berkas tetap berakhiran .csv. */
    private static MockMultipartFile compressedUpload(byte[] original) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(buffer)) {
            gz.write(original);
        }
        return new MockMultipartFile("files", "ukuran.csv", "text/csv", buffer.toByteArray());
    }

    @Test
    @DisplayName("ukuran dataset baru dari CSV terkompresi memakai ukuran asli berkasnya")
    void newDatasetSizeUsesTheOriginalFileSize() throws IOException {
        byte[] original = originalCsv();
        MockMultipartFile upload = compressedUpload(original);

        String expected = datasetFileService.humanSize(original.length);
        assertThat(datasetFileService.humanSize(upload.getSize()))
                .as("prasyarat: ukuran terkirim harus tampil berbeda dari ukuran asli")
                .isNotEqualTo(expected);

        DatasetRequestCreateDTO body = new DatasetRequestCreateDTO();
        body.setTitle("Uji Ukuran Unggah");
        body.setSlug("uji-ukuran-unggah-" + UUID.randomUUID().toString().substring(0, 8));
        body.setTopics(List.of());
        body.setAccessRules(List.of());

        DatasetResponse response = datasetUploadService.upload(publisher, body, List.of(upload));

        assertThat(response.getFileSize()).isEqualTo(expected);
        // Lencana jenis kini juga dihitung dari berkas yang tersimpan, jadi
        // ikut dikunci di sini.
        assertThat(response.getFormats()).containsExactly("CSV");
    }
}
