package id.co.erdigma.satudata.modules.dataset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.AccessRuleType;
import id.co.erdigma.satudata.enums.AuditAction;
import id.co.erdigma.satudata.enums.IdPrefix;
import id.co.erdigma.satudata.exception.BusinessValidationException;
import id.co.erdigma.satudata.modules.audit.service.AuditLogService;
import id.co.erdigma.satudata.modules.dataset.dto.AccessRuleDTO;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetRequestUpdateDTO;
import id.co.erdigma.satudata.modules.dataset.entity.AccessRule;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetResource;
import id.co.erdigma.satudata.modules.dataset.entity.Format;
import id.co.erdigma.satudata.modules.dataset.entity.Topic;
import id.co.erdigma.satudata.modules.dataset.helper.AccessRuleValidator;
import id.co.erdigma.satudata.modules.dataset.helper.AdminDivisionScope;
import id.co.erdigma.satudata.modules.dataset.helper.RichTextSanitizer;
import id.co.erdigma.satudata.modules.dataset.repository.CollectionRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetResourceRepository;
import id.co.erdigma.satudata.modules.dataset.repository.TopicRepository;
import id.co.erdigma.satudata.modules.dataset.service.DatasetFileService.UploadedFile;

import jakarta.persistence.EntityManager;

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
 *
 * <h2>Soal berkas</h2>
 *
 * Sejak formulir sunting dibuat sama persis dengan formulir terbit, berkas ikut
 * bisa disunting, dan {@code body.files} berbentuk KEADAAN AKHIR: yang tidak
 * disebut akan dilepas. Bentuk itu nyaman dipakai formulir tetapi punya satu
 * sisi tajam — permintaan yang lupa menyertakan ruasnya sama sekali tidak boleh
 * terbaca sebagai "hapus semuanya". Itu batasan yang dijaga di sini.
 */
class DatasetUpdateTest {

    private final DatasetRepository datasetRepository = mock(DatasetRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final TopicRepository topicRepository = mock(TopicRepository.class);
    private final CollectionRepository collectionRepository = mock(CollectionRepository.class);
    private final DatasetResourceRepository datasetResourceRepository =
            mock(DatasetResourceRepository.class);
    private final DatasetFileService datasetFileService = mock(DatasetFileService.class);
    private final DatasetService datasetService = mock(DatasetService.class);
    private final AdminDivisionScope adminScope = mock(AdminDivisionScope.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final AccessRuleValidator accessRuleValidator = new AccessRuleValidator();

    /*
      Yang sungguhan, bukan mock.

      Pembersih ini tidak punya kebergantungan apa pun, jadi memakainya apa
      adanya tidak menambah kerumitan. Yang didapat sebagai gantinya: tes
      penyuntingan di bawah ikut membuktikan bahwa deskripsi yang lolos ke
      entity memang deskripsi yang SUDAH dibersihkan. Mock akan meloloskan apa
      pun dan menyembunyikan justru hal itu.
    */
    private final RichTextSanitizer richTextSanitizer = new RichTextSanitizer();

    private final DatasetAdminService service = new DatasetAdminService();

    private Dataset dataset;
    private DatasetResource csv;
    private DatasetResource pdf;

    @BeforeEach
    void wireFields() {
        ReflectionTestUtils.setField(service, "datasetRepository", datasetRepository);
        ReflectionTestUtils.setField(service, "auditLogService", auditLogService);
        ReflectionTestUtils.setField(service, "topicRepository", topicRepository);
        ReflectionTestUtils.setField(service, "collectionRepository", collectionRepository);
        ReflectionTestUtils.setField(service, "datasetResourceRepository", datasetResourceRepository);
        ReflectionTestUtils.setField(service, "datasetFileService", datasetFileService);
        ReflectionTestUtils.setField(service, "datasetService", datasetService);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        ReflectionTestUtils.setField(service, "accessRuleValidator", accessRuleValidator);
        ReflectionTestUtils.setField(service, "richTextSanitizer", richTextSanitizer);

        // Dibiarkan tidak menolak apa pun. Cakupan divisi punya tesnya
        // sendiri; kalau ikut ditegakkan di sini, seluruh kelas ini akan
        // gagal karena alasan yang tidak sedang diujinya.
        ReflectionTestUtils.setField(service, "adminScope", adminScope);

        dataset = new Dataset();
        dataset.setId(UUID.randomUUID());
        dataset.setSlug("penjualan-furnitur-2025");
        dataset.setTitle("Penjualan Furnitur 2025");
        dataset.setNotes("Deskripsi lama.");

        csv = resource("Rekap Penjualan", "CSV", 2048);
        pdf = resource("Kamus Kolom", "PDF", 4096);

        when(datasetRepository.findBySlugAndDeletedAtIsNull("penjualan-furnitur-2025"))
                .thenReturn(Optional.of(dataset));
        when(topicRepository.findAllByDeletedAtIsNullOrderBySortOrderAsc())
                .thenReturn(List.of(topic("Penjualan"), topic("Keuangan")));
        when(datasetFileService.listLive(dataset)).thenReturn(List.of(csv, pdf));
        when(datasetFileService.validate(any(), any(), anyString(), anyInt(), anyLong()))
                .thenReturn(List.of());
    }

    private Topic topic(String name) {
        Topic t = new Topic();
        t.setId(UUID.randomUUID());
        t.setName(name);
        return t;
    }

    private DatasetResource resource(String label, String formatName, long size) {
        Format format = new Format();
        format.setId(UUID.randomUUID());
        format.setName(formatName);

        DatasetResource r = new DatasetResource();
        r.setId(UUID.randomUUID());
        r.setDataset(dataset);
        r.setFormat(format);
        r.setLabel(label);
        r.setSizeBytes(size);
        return r;
    }

    /** Badan permintaan minimal yang sah: judul dan aturan akses selalu wajib. */
    private DatasetRequestUpdateDTO body(String title) {
        DatasetRequestUpdateDTO b = new DatasetRequestUpdateDTO();
        b.setTitle(title);
        b.setAccessRules(List.of());
        return b;
    }

    /**
     * Entri "pertahankan berkas ini", memakai bentuk id yang SUNGGUH dikirim klien.
     *
     * Berawalan, bukan UUID telanjang. Ini bukan detail: seluruh id di API ini
     * keluar berawalan lewat @PrefixedId, jadi itu pula yang kembali dari
     * formulir. Versi pertama tes ini memakai UUID telanjang dan lulus dengan
     * mulus, sementara di peramban setiap penyimpanan ditolak 400 "Permintaan
     * tidak valid." -- tes yang memakai bentuk yang tidak pernah dikirim siapa
     * pun tidak menguji apa-apa.
     */
    private DatasetRequestUpdateDTO.FileEdit keep(DatasetResource resource, String label) {
        DatasetRequestUpdateDTO.FileEdit f = new DatasetRequestUpdateDTO.FileEdit();
        f.setId(IdPrefix.DATASET_RESOURCE.format(resource.getId()));
        f.setLabel(label);
        return f;
    }

    private DatasetRequestUpdateDTO.FileEdit fresh(String label, String format) {
        DatasetRequestUpdateDTO.FileEdit f = new DatasetRequestUpdateDTO.FileEdit();
        f.setLabel(label);
        f.setFormat(format);
        return f;
    }

    private List<MultipartFile> parts(String... names) {
        List<MultipartFile> result = new ArrayList<>();
        for (String name : names) {
            result.add(new MockMultipartFile("files", name, "text/csv", "a,b\n1,2\n".getBytes()));
        }
        return result;
    }

    private String auditDetail() {
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).recordDataset(any(), eq(AuditAction.UPDATE), any(), detail.capture());
        return detail.getValue();
    }

    // ---------------------------------------------------------------- keterangan

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
        service.update(null, "penjualan-furnitur-2025", body("Penjualan Ritel 2025"), null);

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

        service.update(null, "penjualan-furnitur-2025", b, null);

        assertThat(dataset.getNotes()).isEqualTo("Deskripsi lama.");
    }

    @Test
    @DisplayName("string kosong MENGOSONGKAN, berbeda dari ruas yang dihilangkan")
    void emptyStringClearsTheValue() {
        // Bedanya harus tetap ada. Kalau keduanya diperlakukan sama, tidak ada
        // lagi cara menghapus deskripsi lewat API.
        DatasetRequestUpdateDTO b = body("Judul Baru");
        b.setNotes("");

        service.update(null, "penjualan-furnitur-2025", b, null);

        assertThat(dataset.getNotes()).isEmpty();
    }

    @Test
    @DisplayName("deskripsi dibersihkan sebelum tersimpan")
    void descriptionIsSanitizedOnTheWayIn() {
        /*
         * Yang diuji di sini SAMBUNGANNYA, bukan pembersihnya -- itu sudah
         * punya kelas tesnya sendiri. Keduanya bisa rusak sendiri-sendiri:
         * pembersih yang sempurna tetapi tidak dipanggil menyimpan skrip apa
         * adanya, dan tidak ada satu pun galat yang muncul saat itu terjadi.
         *
         * Deskripsi dataset ditulis satu orang dan dibaca banyak orang, jadi
         * yang lolos ke sini akan berjalan di peramban setiap karyawan yang
         * membuka dataset itu.
         */
        DatasetRequestUpdateDTO b = body("Penjualan Furnitur 2025");
        b.setNotes("<p>Rekap <strong>penjualan</strong>.</p><script>alert(1)</script>");

        service.update(null, "penjualan-furnitur-2025", b, null);

        assertThat(dataset.getNotes())
                .contains("<strong>penjualan</strong>")
                .doesNotContain("<script");
    }

    @Test
    @DisplayName("deskripsi yang dibandingkan adalah yang SUDAH dibersihkan")
    void comparisonUsesTheSanitizedValue() {
        // Kalau yang dibandingkan isi mentahnya, permintaan yang isinya sama
        // persis dengan yang tersimpan tetapi membawa satu atribut terlarang
        // akan tercatat di jejak audit sebagai "deskripsi diperbarui" padahal
        // tidak ada yang berubah.
        dataset.setNotes("<p>Deskripsi lama.</p>");

        DatasetRequestUpdateDTO b = body("Penjualan Furnitur 2025");
        b.setNotes("<p onclick=\"alert(1)\">Deskripsi lama.</p>");

        service.update(null, "penjualan-furnitur-2025", b, null);

        assertThat(auditDetail()).contains("tanpa perubahan");
    }

    @Test
    @DisplayName("aturan akses diganti seluruhnya, bukan ditambah")
    void accessRulesAreReplaced() {
        dataset.getAccessRules().add(new AccessRule(AccessRuleType.JOB_LEVEL, "Manager"));

        DatasetRequestUpdateDTO b = body("Penjualan Furnitur 2025");
        b.setAccessRules(List.of(new AccessRuleDTO(AccessRuleType.JOB_LEVEL, "Direktur")));

        service.update(null, "penjualan-furnitur-2025", b, null);

        assertThat(dataset.getAccessRules()).hasSize(1);
        assertThat(dataset.getAccessRules().get(0).getRuleValue()).isEqualTo("Direktur");
    }

    @Test
    @DisplayName("daftar aturan kosong MEMBUKA dataset untuk semua")
    void emptyRulesOpenTheDataset() {
        // Perilaku yang disengaja dan didokumentasikan, jadi harus dikunci supaya
        // perbaikan berikutnya tidak diam-diam menutupnya.
        dataset.getAccessRules().add(new AccessRule(AccessRuleType.JOB_LEVEL, "Manager"));

        service.update(null, "penjualan-furnitur-2025", body("Penjualan Furnitur 2025"), null);

        assertThat(dataset.getAccessRules()).isEmpty();
    }

    @Test
    @DisplayName("topik yang tidak dikenal ditolak, bukan diabaikan")
    void unknownTopicIsRejected() {
        // Diabaikan diam-diam berarti dataset kehilangan topik tanpa ada yang
        // tahu, dan ia berhenti muncul di penyaring topik yang seharusnya.
        DatasetRequestUpdateDTO b = body("Penjualan Furnitur 2025");
        b.setTopics(List.of("Topik Karangan"));

        assertThatThrownBy(() -> service.update(null, "penjualan-furnitur-2025", b, null))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Topik Karangan");
    }

    @Test
    @DisplayName("jejak audit menyebut APA yang berubah, bukan sekadar 'disunting'")
    void auditNamesWhatChanged() {
        // Pertanyaan yang datang belakangan selalu berbentuk "sejak kapan begini",
        // dan catatan tanpa isi tidak menjawabnya.
        service.update(null, "penjualan-furnitur-2025", body("Penjualan Ritel 2025"), null);

        assertThat(auditDetail())
                .contains("Penjualan Furnitur 2025")
                .contains("Penjualan Ritel 2025");
    }

    @Test
    @DisplayName("menyimpan tanpa mengubah apa pun tetap tercatat apa adanya")
    void noOpSaveIsStillRecorded() {
        // Penerbit menekan Simpan dan berhak melihat tindakannya sampai. Yang
        // dicatat apa adanya: tidak ada yang berubah.
        service.update(null, "penjualan-furnitur-2025", body("Penjualan Furnitur 2025"), null);

        assertThat(auditDetail()).contains("tanpa perubahan");
    }

    @Test
    @DisplayName("slug yang tidak dikenal menjawab 404, bukan membuat dataset baru")
    void unknownSlugIsNotFound() {
        assertThatThrownBy(() -> service.update(null, "tidak-ada", body("Apa Saja"), null))
                .isInstanceOf(id.co.erdigma.satudata.exception.ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- berkas

    @Test
    @DisplayName("ruas berkas yang dihilangkan TIDAK melepas satu berkas pun")
    void absentFileListTouchesNothing() {
        /*
         * Sisi tajam dari bentuk keadaan-akhir, dan alasan tes ini ada.
         *
         * Daftar yang dikirim MENGGANTIKAN yang lama, jadi daftar kosong berarti
         * "lepas semuanya". Kalau ruas yang HILANG suatu saat diperlakukan sama
         * dengan daftar kosong, klien yang cuma memperbaiki salah ketik pada
         * judul akan kehilangan seluruh berkas datasetnya — tanpa satu pun galat,
         * karena permintaannya memang sah.
         */
        DatasetRequestUpdateDTO b = body("Judul Baru");
        assertThat(b.getFiles()).isNull();

        service.update(null, "penjualan-furnitur-2025", b, null);

        verify(datasetFileService, never()).remove(any());
        verify(datasetFileService, never()).store(any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("berkas yang tidak disebut dilepas, yang disebut tetap tinggal")
    void unlistedFileIsDropped() {
        DatasetRequestUpdateDTO b = body("Penjualan Furnitur 2025");
        b.setFiles(List.of(keep(csv, "Rekap Penjualan")));

        service.update(null, "penjualan-furnitur-2025", b, null);

        verify(datasetFileService).remove(pdf);
        verify(datasetFileService, never()).remove(csv);
        assertThat(auditDetail()).contains("Kamus Kolom").contains("dilepas");
    }

    @Test
    @DisplayName("melepas SELURUH berkas ditolak, dataset kosong bukan dataset")
    void droppingEveryFileIsRejected() {
        /*
         * Dataset tanpa berkas tidak punya apa pun untuk diunduh maupun
         * ditampilkan, tetapi tetap muncul di katalog sebagai baris yang
         * mengecewakan siapa pun yang membukanya. Kalau memang ingin
         * menghilangkannya, yang benar adalah menghapus datasetnya.
         */
        DatasetRequestUpdateDTO b = body("Penjualan Furnitur 2025");
        b.setFiles(List.of());

        assertThatThrownBy(() -> service.update(null, "penjualan-furnitur-2025", b, null))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("minimal satu berkas");

        verify(datasetFileService, never()).remove(any());
    }

    @Test
    @DisplayName("berkas milik dataset lain ditolak, bukan diabaikan")
    void foreignFileIdIsRejected() {
        // Diabaikan diam-diam berarti entri itu hilang dari daftar yang
        // dipertahankan, dan berkas yang sah justru ikut terlepas.
        DatasetRequestUpdateDTO.FileEdit asing = new DatasetRequestUpdateDTO.FileEdit();
        asing.setId(IdPrefix.DATASET_RESOURCE.format(UUID.randomUUID()));

        DatasetRequestUpdateDTO b = body("Penjualan Furnitur 2025");
        b.setFiles(List.of(keep(csv, "Rekap Penjualan"), keep(pdf, "Kamus Kolom"), asing));

        assertThatThrownBy(() -> service.update(null, "penjualan-furnitur-2025", b, null))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("bukan milik dataset ini");

        verify(datasetFileService, never()).remove(any());
    }

    @Test
    @DisplayName("jumlah berkas baru harus sama dengan jumlah bagian multipart")
    void newEntriesMustMatchTheParts() {
        /*
         * Keduanya dipasangkan menurut urutan. Kalau jumlahnya meleset, setiap
         * berkas mendapat nama milik berkas lain tanpa satu pun galat — dan
         * keterangan yang salah di katalog data lebih berbahaya daripada
         * penolakan.
         */
        DatasetRequestUpdateDTO b = body("Penjualan Furnitur 2025");
        b.setFiles(List.of(keep(csv, "Rekap Penjualan"), keep(pdf, "Kamus Kolom"),
                fresh("Lampiran", "CSV")));

        assertThatThrownBy(() -> service.update(null, "penjualan-furnitur-2025", b, parts()))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("dipasangkan");
    }

    @Test
    @DisplayName("berkas terkirim tanpa entri yang menyatakannya ditolak")
    void partsWithoutEntriesAreRejected() {
        // Kebalikan dari tes di atas: berkas yang muncul di dataset tanpa pernah
        // diminta lebih membingungkan daripada permintaan yang ditolak.
        DatasetRequestUpdateDTO b = body("Penjualan Furnitur 2025");
        b.setFiles(List.of(keep(csv, "Rekap Penjualan"), keep(pdf, "Kamus Kolom")));

        assertThatThrownBy(
                () -> service.update(null, "penjualan-furnitur-2025", b, parts("baru.csv")))
                        .isInstanceOf(BusinessValidationException.class)
                        .hasMessageContaining("dipasangkan");
    }

    @Test
    @DisplayName("berkas baru disimpan TANPA penomoran ulang")
    void newFilesAreStoredWithoutRenumbering() {
        /*
         * Penomoran 1..n hanya aman untuk dataset yang baru dibuat. Di sini
         * nomornya bisa sudah terpakai — termasuk oleh berkas yang sudah dihapus
         * tetapi isinya masih menempati kuncinya di penyimpanan — dan menimpanya
         * tidak menghasilkan galat apa pun, cuma berkas lama yang diam-diam
         * berubah isi.
         */
        when(datasetFileService.validate(any(), any(), anyString(), anyInt(), anyLong()))
                .thenReturn(List.of(mock(UploadedFile.class)));

        DatasetRequestUpdateDTO b = body("Penjualan Furnitur 2025");
        b.setFiles(List.of(keep(csv, "Rekap Penjualan"), keep(pdf, "Kamus Kolom"),
                fresh("Lampiran", "CSV")));

        service.update(null, "penjualan-furnitur-2025", b, parts("lampiran.csv"));

        ArgumentCaptor<Boolean> numbered = ArgumentCaptor.forClass(Boolean.class);
        verify(datasetFileService).store(eq(dataset), any(), numbered.capture());
        assertThat(numbered.getValue()).isFalse();
    }

    @Test
    @DisplayName("mengganti nama berkas tidak menyentuh isinya")
    void renamingAFileLeavesItInPlace() {
        DatasetRequestUpdateDTO b = body("Penjualan Furnitur 2025");
        b.setFiles(List.of(keep(csv, "Rekap Penjualan Ritel"), keep(pdf, "Kamus Kolom")));

        service.update(null, "penjualan-furnitur-2025", b, null);

        assertThat(csv.getLabel()).isEqualTo("Rekap Penjualan Ritel");
        verify(datasetFileService, never()).remove(any());
        // Tidak ada berkas yang keluar-masuk, jadi tidak ada yang perlu dibaca
        // ulang maupun dihitung ulang.
        verify(datasetFileService, never()).store(any(), any(), anyBoolean());
        assertThat(auditDetail()).contains("Rekap Penjualan Ritel");
    }

    @Test
    @DisplayName("nama berkas kosong berarti tidak disebut, bukan dikosongkan")
    void blankLabelLeavesTheNameAlone() {
        // Berkas tanpa nama tidak punya apa pun untuk ditampilkan di tab Data
        // Explorer selain nama berkas mentahnya.
        DatasetRequestUpdateDTO b = body("Penjualan Furnitur 2025");
        b.setFiles(List.of(keep(csv, "   "), keep(pdf, null)));

        service.update(null, "penjualan-furnitur-2025", b, null);

        assertThat(csv.getLabel()).isEqualTo("Rekap Penjualan");
        assertThat(pdf.getLabel()).isEqualTo("Kamus Kolom");
    }

    @Test
    @DisplayName("berkas lama yang dipertahankan ikut dihitung terhadap batas")
    void keptFilesCountTowardTheLimit() {
        /*
         * Batas 10 berkas dan 40 MB berlaku untuk DATASET, bukan untuk satu
         * permintaan. Kalau yang dihitung hanya berkas baru, dataset bisa
         * tumbuh melewati batasnya lewat penyuntingan berulang — masing-masing
         * sah kalau dilihat sendiri-sendiri.
         */
        DatasetRequestUpdateDTO b = body("Penjualan Furnitur 2025");
        b.setFiles(List.of(keep(csv, "Rekap Penjualan"), keep(pdf, "Kamus Kolom")));

        service.update(null, "penjualan-furnitur-2025", b, null);

        ArgumentCaptor<Integer> count = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Long> bytes = ArgumentCaptor.forClass(Long.class);
        verify(datasetFileService).validate(any(), any(), anyString(), count.capture(),
                bytes.capture());

        assertThat(count.getValue()).isEqualTo(2);
        assertThat(bytes.getValue()).isEqualTo(2048L + 4096L);
    }

    @Test
    @DisplayName("berkas yang sama disebut dua kali ditolak")
    void duplicateFileEntryIsRejected() {
        // Kalau dibiarkan, entri kedua akan menimpa nama yang baru saja disetel
        // entri pertama, dan yang tersimpan bergantung pada urutan daftar.
        DatasetRequestUpdateDTO b = body("Penjualan Furnitur 2025");
        b.setFiles(List.of(keep(csv, "Nama A"), keep(csv, "Nama B"), keep(pdf, "Kamus Kolom")));

        assertThatThrownBy(() -> service.update(null, "penjualan-furnitur-2025", b, null))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("lebih dari sekali");
    }

    @Test
    @DisplayName("id berawalan seperti yang dikirim API diterima apa adanya")
    void prefixedIdIsAccepted() {
        /*
         * Bentuk inilah yang sungguh melintas: GET mengirim "dres-<uuid>", dan
         * formulir mengembalikannya apa adanya. Sempat ditolak karena ruasnya
         * bertipe UUID, dan penolakannya terjadi di lapisan Jackson -- sebelum
         * satu baris kode ini berjalan, sehingga tidak ada satu pun tes lama
         * yang bisa melihatnya.
         */
        DatasetRequestUpdateDTO b = body("Penjualan Furnitur 2025");
        b.setFiles(List.of(keep(csv, "Rekap Penjualan Ritel"), keep(pdf, "Kamus Kolom")));

        assertThat(b.getFiles().get(0).getId()).startsWith("dres-");

        service.update(null, "penjualan-furnitur-2025", b, null);

        assertThat(csv.getLabel()).isEqualTo("Rekap Penjualan Ritel");
        verify(datasetFileService, never()).remove(any());
    }

    @Test
    @DisplayName("UUID telanjang tanpa awalan tetap diterima")
    void bareUuidStillWorks() {
        // Klien lama dan skrip yang ditulis sebelum awalan ada tidak perlu ikut
        // rusak. Kelonggarannya milik IdPrefix.parse, dan dikunci di sini supaya
        // tidak hilang tanpa sengaja.
        DatasetRequestUpdateDTO.FileEdit telanjang = new DatasetRequestUpdateDTO.FileEdit();
        telanjang.setId(csv.getId().toString());
        telanjang.setLabel("Rekap Penjualan");

        DatasetRequestUpdateDTO b = body("Penjualan Furnitur 2025");
        b.setFiles(List.of(telanjang, keep(pdf, "Kamus Kolom")));

        service.update(null, "penjualan-furnitur-2025", b, null);

        verify(datasetFileService, never()).remove(any());
    }

    @Test
    @DisplayName("id milik tabel lain ditolak dengan pesan yang menyebut bentuk yang benar")
    void foreignPrefixIsRejected() {
        // Mengirim id pengguna ke daftar berkas lebih mungkin berarti salah
        // salin daripada serangan, dan pesan yang jelas menghemat waktu.
        DatasetRequestUpdateDTO.FileEdit salahTabel = new DatasetRequestUpdateDTO.FileEdit();
        salahTabel.setId(IdPrefix.USER.format(csv.getId()));

        DatasetRequestUpdateDTO b = body("Penjualan Furnitur 2025");
        b.setFiles(List.of(salahTabel));

        assertThatThrownBy(() -> service.update(null, "penjualan-furnitur-2025", b, null))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("dres-");
    }

    @Test
    @DisplayName("pengunggah asli tidak tergantikan penyunting")
    void editorDoesNotReplaceTheUploader() {
        // Kolom itu jejak siapa yang menerbitkan, bukan penanda pemilik saat
        // ini. Yang mencatat siapa menyunting apa adalah log audit.
        User asli = new User();
        asli.setId(UUID.randomUUID());
        dataset.setUploadedBy(asli);

        User penyunting = new User();
        penyunting.setId(UUID.randomUUID());

        service.update(penyunting, "penjualan-furnitur-2025", body("Judul Baru"), null);

        assertThat(dataset.getUploadedBy()).isSameAs(asli);
    }
}
