package id.co.erdigma.satudata.modules.dataset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import id.co.erdigma.satudata.exception.BusinessValidationException;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetRequestCreateDTO;
import id.co.erdigma.satudata.modules.dataset.dto.FileMetaView;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.entity.Format;
import id.co.erdigma.satudata.modules.dataset.repository.FormatRepository;
import id.co.erdigma.satudata.modules.dataset.service.DatasetFileService.UploadedFile;

/**
 * Penjagaan yang berdiri di antara unggahan dan penyimpanan.
 *
 * <h2>Kenapa kelas ini ada terpisah</h2>
 *
 * {@link DatasetUpdateTest} menguji keputusan penyuntingan dengan layanan ini
 * di-mock, dan itu memang yang seharusnya di sana. Akibatnya isi layanan ini
 * sendiri tidak pernah dijalankan satu baris pun oleh tes itu -- padahal di
 * sinilah batas ukuran, batas jumlah, dan pencocokan jenis berkas berada.
 *
 * Semua itu urusan keamanan, dan sejak penerbitan serta penyuntingan memakainya
 * bersama, kegagalannya berlaku di kedua jalur sekaligus.
 *
 * <h2>Yang paling menentukan</h2>
 *
 * {@code freeFileName} yang keliru <b>tidak menghasilkan galat apa pun</b>.
 * Berkas baru cuma disimpan dengan kunci yang sudah ditempati berkas lain, dan
 * penyimpanan objek dengan senang hati menulis ulang kunci yang sudah ada. Yang
 * terlihat belakangan: sebuah dataset lama yang isinya diam-diam berubah, tanpa
 * satu pun catatan yang menghubungkannya dengan penyuntingan dataset lain.
 */
class DatasetFileServiceTest {

    private final FormatRepository formatRepository = mock(FormatRepository.class);
    private final DatasetFileService service = new DatasetFileService();

    private Dataset dataset;

    @BeforeEach
    void wireFields() {
        ReflectionTestUtils.setField(service, "formatRepository", formatRepository);

        dataset = new Dataset();
        dataset.setId(UUID.randomUUID());
        dataset.setSlug("penjualan-furnitur-2025");
        dataset.setTitle("Penjualan Furnitur 2025");

        when(formatRepository.findAllByDeletedAtIsNullOrderBySortOrderAsc())
                .thenReturn(List.of(format("CSV"), format("XLSX"), format("PDF"), format("DOCX")));
    }

    private Format format(String name) {
        Format f = new Format();
        f.setId(UUID.randomUUID());
        f.setName(name);
        return f;
    }

    private MultipartFile part(String name, int bytes) {
        return new MockMultipartFile("files", name, "application/octet-stream", new byte[bytes]);
    }

    private List<MultipartFile> parts(MultipartFile... items) {
        return new ArrayList<>(List.of(items));
    }

    private FileMetaView meta(String label, String format) {
        DatasetRequestCreateDTO.FileMeta m = new DatasetRequestCreateDTO.FileMeta();
        m.setLabel(label);
        m.setFormat(format);
        return m;
    }

    // ------------------------------------------------------- pencocokan urutan

    @Test
    @DisplayName("keterangan dipasangkan menurut URUTAN, bukan menurut nama berkas")
    void metadataIsPairedByPosition() {
        /*
         * Perilaku yang mudah dikira "dipasangkan menurut nama". Kalau suatu
         * saat ada yang menukarnya, setiap berkas mendapat nama milik berkas
         * lain tanpa satu pun galat, dan katalog datanya berbohong dengan rapi.
         */
        List<UploadedFile> hasil = service.validate(
                List.of(meta("Kamus Kolom", "PDF"), meta("Rekap Penjualan", "CSV")),
                parts(part("lampiran.pdf", 10), part("data.csv", 10)),
                "Judul Dataset", 0, 0L);

        assertThat(hasil).hasSize(2);
        assertThat(hasil.get(0).label()).isEqualTo("Kamus Kolom");
        assertThat(hasil.get(0).originalName()).isEqualTo("lampiran.pdf");
        assertThat(hasil.get(1).label()).isEqualTo("Rekap Penjualan");
        assertThat(hasil.get(1).originalName()).isEqualTo("data.csv");
    }

    @Test
    @DisplayName("jumlah keterangan yang tidak sama dengan jumlah berkas ditolak")
    void mismatchedCountsAreRejected() {
        assertThatThrownBy(() -> service.validate(
                List.of(meta("Satu", "CSV")),
                parts(part("a.csv", 10), part("b.csv", 10)),
                "Judul", 0, 0L))
                        .isInstanceOf(BusinessValidationException.class)
                        .hasMessageContaining("dipasangkan menurut urutan");
    }

    @Test
    @DisplayName("tanpa keterangan sama sekali, namanya jatuh ke judul dataset")
    void missingMetadataFallsBackToTheTitle() {
        List<UploadedFile> hasil = service.validate(null, parts(part("data.csv", 10)),
                "Penjualan Furnitur 2025", 0, 0L);

        assertThat(hasil).hasSize(1);
        assertThat(hasil.get(0).label()).isEqualTo("Penjualan Furnitur 2025");
    }

    // ------------------------------------------------------------ jenis berkas

    @Test
    @DisplayName("jenis yang dinyatakan diperiksa terhadap ekstensi yang sungguh dikirim")
    void declaredFormatMustMatchTheExtension() {
        /*
         * Lencana "PDF" pada berkas yang isinya CSV adalah keterangan salah, dan
         * keterangan salah di katalog data lebih berbahaya daripada penolakan:
         * orang mengunduhnya dengan harapan yang keliru, dan yang salah bukan
         * dugaannya.
         */
        assertThatThrownBy(() -> service.validate(
                List.of(meta("Menyamar", "PDF")),
                parts(part("sebenarnya.csv", 10)),
                "Judul", 0, 0L))
                        .isInstanceOf(BusinessValidationException.class)
                        .hasMessageContaining("berekstensi .csv");
    }

    @Test
    @DisplayName("jenis dibaca dari ekstensi kalau tidak dinyatakan")
    void formatIsReadFromTheExtension() {
        List<UploadedFile> hasil = service.validate(
                List.of(meta("Lampiran", null)),
                parts(part("lampiran.pdf", 10)),
                "Judul", 0, 0L);

        assertThat(hasil.get(0).format().getName()).isEqualTo("PDF");
    }

    @Test
    @DisplayName("ekstensi di luar keempat yang didukung ditolak")
    void unsupportedExtensionIsRejected() {
        assertThatThrownBy(() -> service.validate(null, parts(part("arsip.zip", 10)),
                "Judul", 0, 0L))
                        .isInstanceOf(BusinessValidationException.class)
                        .hasMessageContaining("tidak didukung");
    }

    @Test
    @DisplayName("berkas tanpa ekstensi ditolak, bukan diterima sebagai jenis apa saja")
    void extensionlessFileIsRejected() {
        assertThatThrownBy(() -> service.validate(null, parts(part("data", 10)),
                "Judul", 0, 0L))
                        .isInstanceOf(BusinessValidationException.class)
                        .hasMessageContaining("tidak didukung");
    }

    // ----------------------------------------------------------------- batasan

    @Test
    @DisplayName("satu berkas melebihi batas per berkas ditolak")
    void oversizedFileIsRejected() {
        assertThatThrownBy(() -> service.validate(null,
                parts(part("besar.csv", (int) DatasetFileService.MAX_BYTES + 1)),
                "Judul", 0, 0L))
                        .isInstanceOf(BusinessValidationException.class)
                        .hasMessageContaining("melebihi batas");
    }

    @Test
    @DisplayName("berkas lama yang dipertahankan ikut dihitung terhadap batas UKURAN")
    void keptBytesCountTowardTheTotalLimit() {
        /*
         * Batasnya milik DATASET, bukan milik satu permintaan. Kalau yang
         * dihitung hanya berkas baru, dataset bisa tumbuh melewati batasnya
         * lewat penyuntingan berulang -- masing-masing sah kalau dilihat
         * sendiri-sendiri, dan tidak ada satu titik pun yang bisa menolaknya.
         */
        long hampirPenuh = DatasetFileService.MAX_TOTAL_BYTES - 100;

        assertThatCode(() -> service.validate(null, parts(part("kecil.csv", 100)),
                "Judul", 1, hampirPenuh)).doesNotThrowAnyException();

        assertThatThrownBy(() -> service.validate(null, parts(part("kecil.csv", 101)),
                "Judul", 1, hampirPenuh))
                        .isInstanceOf(BusinessValidationException.class)
                        .hasMessageContaining("Total ukuran");
    }

    @Test
    @DisplayName("berkas lama yang dipertahankan ikut dihitung terhadap batas JUMLAH")
    void keptCountCountsTowardTheFileLimit() {
        int sisa = DatasetFileService.MAX_FILES - 1;

        assertThatCode(() -> service.validate(null, parts(part("a.csv", 10)),
                "Judul", sisa, 0L)).doesNotThrowAnyException();

        assertThatThrownBy(() -> service.validate(null,
                parts(part("a.csv", 10), part("b.csv", 10)), "Judul", sisa, 0L))
                        .isInstanceOf(BusinessValidationException.class)
                        .hasMessageContaining("Maksimal " + DatasetFileService.MAX_FILES);
    }

    @Test
    @DisplayName("bagian multipart yang kosong diabaikan, bukan dihitung sebagai berkas")
    void emptyPartsAreIgnored() {
        // Peramban mengirim bagian kosong untuk input file yang tidak diisi.
        // Menghitungnya sebagai berkas membuat jumlahnya tidak pernah cocok
        // dengan jumlah keterangan.
        List<MultipartFile> dengan = parts(part("data.csv", 10));
        dengan.add(new MockMultipartFile("files", "", "application/octet-stream", new byte[0]));

        List<UploadedFile> hasil = service.validate(List.of(meta("Data", "CSV")), dengan,
                "Judul", 0, 0L);

        assertThat(hasil).hasSize(1);
    }

    @Test
    @DisplayName("tanpa berkas baru sama sekali tetap sah")
    void noNewFilesIsFine() {
        // Penyuntingan yang cuma mengganti nama berkas tidak mengirim satu pun
        // bagian multipart. Penolakan "minimal satu berkas" milik penerbitan,
        // dan memang ditahan di sana.
        assertThat(service.validate(List.of(), null, "Judul", 2, 1024L)).isEmpty();
    }

    // ---------------------------------------------------------- nama berkas

    @Test
    @DisplayName("nama berkas baru MELEWATI nama yang sudah terpakai")
    void newFileNameSkipsTakenOnes() {
        /*
         * Tes paling menentukan di kelas ini.
         *
         * Kegagalannya tidak menghasilkan galat: berkasnya cuma disimpan dengan
         * kunci yang sudah ditempati berkas lain, dan penyimpanan objek menulis
         * ulang kunci yang sudah ada tanpa mengeluh. Yang terlihat belakangan
         * adalah dataset lama yang isinya berubah sendiri.
         */
        Format csv = format("CSV");
        Set<String> taken = new HashSet<>(Set.of(
                "penjualan-furnitur-2025.csv",
                "penjualan-furnitur-2025-2.csv"));

        assertThat(DatasetFileService.freeFileName(dataset, csv, taken))
                .isEqualTo("penjualan-furnitur-2025-3.csv");
    }

    @Test
    @DisplayName("nama yang baru diberikan langsung ikut dianggap terpakai")
    void issuedNamesAreImmediatelyReserved() {
        // Dua berkas dalam satu permintaan yang sama tidak boleh mendapat nama
        // yang sama. Kalau iya, yang kedua menimpa yang pertama di detik yang
        // sama, dan keduanya berasal dari permintaan yang sedang berjalan.
        Format csv = format("CSV");
        Set<String> taken = new HashSet<>();

        String pertama = DatasetFileService.freeFileName(dataset, csv, taken);
        String kedua = DatasetFileService.freeFileName(dataset, csv, taken);

        assertThat(pertama).isEqualTo("penjualan-furnitur-2025.csv");
        assertThat(kedua).isEqualTo("penjualan-furnitur-2025-2.csv");
    }

    @Test
    @DisplayName("jenis berbeda tidak saling menghalangi")
    void differentFormatsDoNotBlockEachOther() {
        // Kuncinya berakhiran ekstensi, jadi .csv dan .pdf tidak pernah bertabrakan
        // meski nomornya sama.
        Set<String> taken = new HashSet<>(Set.of("penjualan-furnitur-2025.csv"));

        assertThat(DatasetFileService.freeFileName(dataset, format("PDF"), taken))
                .isEqualTo("penjualan-furnitur-2025.pdf");
    }
}
