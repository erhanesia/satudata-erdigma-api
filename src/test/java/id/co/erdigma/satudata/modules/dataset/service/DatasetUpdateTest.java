package id.co.erdigma.satudata.modules.dataset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.AccessRuleType;
import id.co.erdigma.satudata.enums.AuditAction;
import id.co.erdigma.satudata.exception.BusinessValidationException;
import id.co.erdigma.satudata.modules.audit.service.AuditLogService;
import id.co.erdigma.satudata.modules.dataset.dto.AccessRuleDTO;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetRequestUpdateDTO;
import id.co.erdigma.satudata.modules.dataset.entity.AccessRule;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.entity.Topic;
import id.co.erdigma.satudata.modules.dataset.helper.AccessRuleValidator;
import id.co.erdigma.satudata.modules.dataset.mapper.DatasetMapper;
import id.co.erdigma.satudata.modules.dataset.repository.CollectionRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;
import id.co.erdigma.satudata.modules.dataset.repository.TopicRepository;

/**
 * Menjaga keputusan-keputusan yang melekat pada penyuntingan dataset.
 *
 * <h2>Kenapa kelas ini ada</h2>
 *
 * Sebagian besar yang diuji di sini bukan perhitungan yang bisa keliru,
 * melainkan <b>batasan yang sengaja dipasang</b> — dan batasan semacam itu justru
 * yang paling mudah tergerus. Orang berikutnya yang menambahkan satu ruas ke
 * formulir edit tidak akan tahu bahwa slug sengaja tidak ikut berubah, kecuali
 * ada yang gagal saat ia melakukannya.
 *
 * Yang paling menentukan: <b>slug tidak boleh ikut berubah saat judul diganti</b>.
 * Kalau itu rusak, tidak ada satu pun galat yang muncul — tautan yang sudah
 * beredar di grup dan catatan rapat cuma berhenti bekerja, dan tidak ada yang
 * menghubungkannya dengan penyuntingan judul minggu lalu.
 */
class DatasetUpdateTest {

    private final DatasetRepository datasetRepository = mock(DatasetRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final DatasetMapper datasetMapper = mock(DatasetMapper.class);
    private final TopicRepository topicRepository = mock(TopicRepository.class);
    private final CollectionRepository collectionRepository = mock(CollectionRepository.class);
    private final AccessRuleValidator accessRuleValidator = new AccessRuleValidator();

    private final DatasetAdminService service = new DatasetAdminService();

    private Dataset dataset;

    @BeforeEach
    void wireFields() {
        ReflectionTestUtils.setField(service, "datasetRepository", datasetRepository);
        ReflectionTestUtils.setField(service, "auditLogService", auditLogService);
        ReflectionTestUtils.setField(service, "datasetMapper", datasetMapper);
        ReflectionTestUtils.setField(service, "topicRepository", topicRepository);
        ReflectionTestUtils.setField(service, "collectionRepository", collectionRepository);
        ReflectionTestUtils.setField(service, "accessRuleValidator", accessRuleValidator);

        dataset = new Dataset();
        dataset.setId(UUID.randomUUID());
        dataset.setSlug("penjualan-furnitur-2025");
        dataset.setTitle("Penjualan Furnitur 2025");
        dataset.setNotes("Deskripsi lama.");

        when(datasetRepository.findBySlugAndDeletedAtIsNull("penjualan-furnitur-2025"))
                .thenReturn(Optional.of(dataset));
        when(topicRepository.findAllByDeletedAtIsNullOrderBySortOrderAsc())
                .thenReturn(List.of(topic("Penjualan"), topic("Keuangan")));
    }

    private Topic topic(String name) {
        Topic t = new Topic();
        t.setId(UUID.randomUUID());
        t.setName(name);
        return t;
    }

    /** Badan permintaan minimal yang sah: judul dan aturan akses selalu wajib. */
    private DatasetRequestUpdateDTO body(String title) {
        DatasetRequestUpdateDTO b = new DatasetRequestUpdateDTO();
        b.setTitle(title);
        b.setAccessRules(List.of());
        return b;
    }

    private String auditDetail() {
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).recordDataset(any(), eq(AuditAction.UPDATE), any(), detail.capture());
        return detail.getValue();
    }

    @Test
    @DisplayName("judul berubah, slug TIDAK ikut berubah")
    void titleChangesButSlugDoesNot() {
        /*
         * Tes paling menentukan di kelas ini.
         *
         * Kalau slug ikut berubah, tidak ada yang gagal dan tidak ada galat yang
         * muncul. Yang terjadi hanya setiap tautan yang sudah dibagikan berhenti
         * bekerja, tanpa ada yang menghubungkannya dengan penyuntingan judul.
         */
        service.update(null, "penjualan-furnitur-2025", body("Penjualan Ritel 2025"));

        assertThat(dataset.getTitle()).isEqualTo("Penjualan Ritel 2025");
        assertThat(dataset.getSlug()).isEqualTo("penjualan-furnitur-2025");
    }

    @Test
    @DisplayName("ruas keterangan yang dihilangkan TIDAK menghapus isinya")
    void absentFieldLeavesValueAlone() {
        // Klien yang hanya ingin mengganti judul tidak boleh kehilangan deskripsi
        // yang tidak ia sentuh.
        DatasetRequestUpdateDTO b = body("Judul Baru");
        assertThat(b.getNotes()).isNull();

        service.update(null, "penjualan-furnitur-2025", b);

        assertThat(dataset.getNotes()).isEqualTo("Deskripsi lama.");
    }

    @Test
    @DisplayName("string kosong MENGOSONGKAN, berbeda dari ruas yang dihilangkan")
    void emptyStringClearsTheValue() {
        // Bedanya harus tetap ada. Kalau keduanya diperlakukan sama, tidak ada
        // lagi cara menghapus deskripsi lewat API.
        DatasetRequestUpdateDTO b = body("Judul Baru");
        b.setNotes("");

        service.update(null, "penjualan-furnitur-2025", b);

        assertThat(dataset.getNotes()).isEmpty();
    }

    @Test
    @DisplayName("aturan akses diganti seluruhnya, bukan ditambah")
    void accessRulesAreReplaced() {
        dataset.getAccessRules().add(new AccessRule(AccessRuleType.JOB_LEVEL, "Manager"));

        DatasetRequestUpdateDTO b = body("Penjualan Furnitur 2025");
        b.setAccessRules(List.of(new AccessRuleDTO(AccessRuleType.JOB_LEVEL, "Direktur")));

        service.update(null, "penjualan-furnitur-2025", b);

        assertThat(dataset.getAccessRules()).hasSize(1);
        assertThat(dataset.getAccessRules().get(0).getRuleValue()).isEqualTo("Direktur");
    }

    @Test
    @DisplayName("daftar aturan kosong MEMBUKA dataset untuk semua")
    void emptyRulesOpenTheDataset() {
        // Perilaku yang disengaja dan didokumentasikan, jadi harus dikunci supaya
        // perbaikan berikutnya tidak diam-diam menutupnya.
        dataset.getAccessRules().add(new AccessRule(AccessRuleType.JOB_LEVEL, "Manager"));

        service.update(null, "penjualan-furnitur-2025", body("Penjualan Furnitur 2025"));

        assertThat(dataset.getAccessRules()).isEmpty();
    }

    @Test
    @DisplayName("topik yang tidak dikenal ditolak, bukan diabaikan")
    void unknownTopicIsRejected() {
        // Diabaikan diam-diam berarti dataset kehilangan topik tanpa ada yang
        // tahu, dan ia berhenti muncul di penyaring topik yang seharusnya.
        DatasetRequestUpdateDTO b = body("Penjualan Furnitur 2025");
        b.setTopics(List.of("Topik Karangan"));

        assertThatThrownBy(() -> service.update(null, "penjualan-furnitur-2025", b))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Topik Karangan");
    }

    @Test
    @DisplayName("jejak audit menyebut APA yang berubah, bukan sekadar 'disunting'")
    void auditNamesWhatChanged() {
        // Pertanyaan yang datang belakangan selalu berbentuk "sejak kapan begini",
        // dan catatan tanpa isi tidak menjawabnya.
        service.update(null, "penjualan-furnitur-2025", body("Penjualan Ritel 2025"));

        assertThat(auditDetail())
                .contains("Penjualan Furnitur 2025")
                .contains("Penjualan Ritel 2025");
    }

    @Test
    @DisplayName("menyimpan tanpa mengubah apa pun tetap tercatat apa adanya")
    void noOpSaveIsStillRecorded() {
        // Penerbit menekan Simpan dan berhak melihat tindakannya sampai. Yang
        // dicatat apa adanya: tidak ada yang berubah.
        service.update(null, "penjualan-furnitur-2025", body("Penjualan Furnitur 2025"));

        assertThat(auditDetail()).contains("tanpa perubahan");
    }

    @Test
    @DisplayName("slug yang tidak dikenal menjawab 404, bukan membuat dataset baru")
    void unknownSlugIsNotFound() {
        assertThatThrownBy(() -> service.update(null, "tidak-ada", body("Apa Saja")))
                .isInstanceOf(id.co.erdigma.satudata.exception.ResourceNotFoundException.class);
    }
}
