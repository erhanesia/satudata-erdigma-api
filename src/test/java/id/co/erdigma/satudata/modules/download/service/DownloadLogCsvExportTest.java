package id.co.erdigma.satudata.modules.download.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import id.co.erdigma.satudata.modules.audit.service.AuditLogService;
import id.co.erdigma.satudata.modules.dataset.helper.AdminDivisionScope;
import id.co.erdigma.satudata.modules.download.entity.DownloadLog;
import id.co.erdigma.satudata.modules.download.repository.DownloadLogRepository;

/**
 * Mengunci netralisasi rumus pada ekspor CSV log unduhan.
 *
 * <h2>Kenapa kelas ini ada</h2>
 *
 * Netralisasi rumus adalah satu-satunya perilaku keamanan pada jalur ekspor, dan
 * ia punya sifat yang membuatnya berbahaya kalau tidak dikunci: <b>kerusakannya
 * senyap</b>. Kalau daftar karakternya suatu saat dipersempit — misalnya
 * {@code "=+-@\t\r"} dirapikan jadi {@code "=+-@"} karena dua yang terakhir
 * dikira salah ketik — tidak ada yang gagal. Kompilasi lolos, ekspor tetap
 * jalan, berkasnya tetap terbuka normal. Yang hilang hanya perlindungannya, dan
 * tidak ada satu pun sinyal yang memberi tahu.
 *
 * <h2>Kenapa lewat exportCsv, bukan memanggil columns() langsung</h2>
 *
 * {@code columns()} berstatus private, dan membukanya semata-mata supaya bisa
 * dites berarti mengubah desain kelas karena tuntutan alat. Lewat
 * {@code exportCsv} tesnya menempuh jalur yang sama dengan yang ditempuh admin
 * sungguhan, sekaligus ikut menguji hal yang tidak terlihat kalau metodenya
 * dipanggil sendirian: urutan antara netralisasi dan pengutipan.
 */
class DownloadLogCsvExportTest {

    private final DownloadLogRepository downloadLogRepository = mock(DownloadLogRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final AdminDivisionScope adminScope = mock(AdminDivisionScope.class);
    private final DownloadLogService service = new DownloadLogService();

    @BeforeEach
    void wireFields() {
        ReflectionTestUtils.setField(service, "downloadLogRepository", downloadLogRepository);
        ReflectionTestUtils.setField(service, "auditLogService", auditLogService);
        ReflectionTestUtils.setField(service, "adminScope", adminScope);

        // Yang diuji di sini netralisasi rumus, bukan cakupan divisi, jadi
        // cakupannya dibuat seluas mungkin: null berarti tidak dibatasi divisi.
        when(adminScope.filterDivisionId(any())).thenReturn(null);
    }

    /**
     * Membuat satu baris log yang seluruh ruas teksnya bernilai wajar, kecuali
     * nama berkasnya.
     *
     * Nama berkas dipilih sebagai titik uji karena itulah jalur masuk yang nyata:
     * nilainya datang dari nama berkas yang diunggah, dan DatasetUploadService
     * hanya memeriksa ekstensinya.
     */
    private String exportWithFileName(String fileName) {
        DownloadLog row = new DownloadLog();
        row.setDownloadedAt(LocalDateTime.of(2026, 9, 8, 10, 30));
        row.setAccessType("DOWNLOAD");
        row.setUserName("Budi Santoso");
        row.setUserEmail("budi@erdigma.co.id");
        row.setDivisionCode("DIT");
        row.setDatasetSlug("laporan-penjualan");
        row.setFileName(fileName);
        row.setSizeBytes(1024);
        row.setChannel("WEB");
        row.setAgreementAccepted(true);
        row.setIpAddress("10.0.0.1");

        Page<DownloadLog> page = new PageImpl<>(List.of(row));
        when(downloadLogRepository.search(
                any(LocalDateTime.class), any(LocalDateTime.class), any(), any(),
                any(Pageable.class)))
                .thenReturn(page);

        return service.exportCsv(null, null, null, null);
    }

    /** Baris isi, yaitu seluruh keluaran dikurangi baris kepala kolom. */
    private String dataRow(String csv) {
        int newline = csv.indexOf('\n');
        return csv.substring(newline + 1);
    }

    @Test
    @DisplayName("empat karakter rumus yang terlihat mata diberi kutip tunggal")
    void neutralisesVisibleFormulaPrefixes() {
        assertThat(dataRow(exportWithFileName("=1+1"))).contains(",'=1+1,");
        assertThat(dataRow(exportWithFileName("+1"))).contains(",'+1,");
        assertThat(dataRow(exportWithFileName("@SUM(A1)"))).contains(",'@SUM(A1),");
    }

    @Test
    @DisplayName("nama berkas berawalan tanda hubung ikut dinetralkan")
    void neutralisesLeadingHyphen() {
        // Kasus tanpa niat jahat, dan yang paling mungkin benar-benar terjadi.
        // Tanpa penjagaan ini "-rekap-2026.csv" tampil sebagai #NAME? di Excel,
        // alih-alih namanya sendiri.
        assertThat(dataRow(exportWithFileName("-rekap-2026.csv"))).contains(",'-rekap-2026.csv,");
    }

    @Test
    @DisplayName("tab dan carriage return di awal nilai ikut dinetralkan")
    void neutralisesInvisiblePrefixes() {
        // Dua karakter inilah yang paling rawan hilang saat seseorang merapikan
        // konstantanya, karena keduanya tidak terlihat mata di layar editor.
        // Aplikasi spreadsheet mengabaikan spasi awal, sehingga "\t=1+1" tetap
        // dibaca sebagai rumus.
        assertThat(dataRow(exportWithFileName("\t=1+1"))).contains(",'\t=1+1,");
        assertThat(dataRow(exportWithFileName("\r=1+1"))).contains(",'\r=1+1,");
    }

    @Test
    @DisplayName("nilai wajar TIDAK diberi kutip tunggal maupun tanda kutip")
    void leavesOrdinaryValuesAlone() {
        // Arah kegagalan yang berlawanan, dan sama merusaknya. Kalau suatu saat
        // ada yang "mengamankan" ini dengan memberi awalan pada SEMUA nilai,
        // seluruh nama berkas di hasil ekspor jadi berawalan kutip yang salah,
        // dan tidak ada tes lain yang akan menangkapnya.
        String row = dataRow(exportWithFileName("laporan-penjualan.csv"));

        assertThat(row).contains(",laporan-penjualan.csv,");
        assertThat(row).doesNotContain("'laporan");
        assertThat(row).doesNotContain("\"laporan");
    }

    @Test
    @DisplayName("nilai kosong dan null tidak menggagalkan ekspor")
    void handlesEmptyAndNull() {
        assertThat(dataRow(exportWithFileName(""))).contains(",,");
        assertThat(dataRow(exportWithFileName(null))).contains(",,");
    }

    @Test
    @DisplayName("nilai berkoma tetap dikutip, dan tanda kutip di dalamnya digandakan")
    void quotesStructuralCharacters() {
        // Ini soal FORMAT, bukan keamanan. Diuji di sini supaya perbaikan pada
        // salah satunya tidak diam-diam merusak yang lain.
        assertThat(dataRow(exportWithFileName("laporan, final.csv")))
                .contains("\"laporan, final.csv\"");
        assertThat(dataRow(exportWithFileName("laporan \"final\".csv")))
                .contains("\"laporan \"\"final\"\".csv\"");
    }

    @Test
    @DisplayName("nilai yang sekaligus rumus dan berkoma: kutip tunggal berada DI DALAM tanda kutip")
    void neutralisationHappensBeforeQuoting() {
        /*
         * Tes yang paling menentukan di kelas ini, dan satu-satunya yang tidak
         * terlihat kalau columns() dipanggil sendirian dengan nilai sederhana.
         *
         * Kedua penanganan itu harus berjalan dalam urutan tertentu. Kalau
         * pengutipan dikerjakan lebih dulu, hasilnya '"=SUM(A1,B1)" -- kutip
         * tunggalnya berada di LUAR, sehingga pembaca CSV melihat sel yang
         * diawali tanda kutip tunggal dan isinya tetap dimulai tanda sama dengan.
         * Perlindungannya hilang, padahal kodenya terlihat seperti masih ada.
         */
        String row = dataRow(exportWithFileName("=SUM(A1,B1)"));

        assertThat(row).contains("\"'=SUM(A1,B1)\"");
        assertThat(row).doesNotContain("'\"=SUM");
    }

    @Test
    @DisplayName("baris kepala kolom tetap ditulis meski tidak ada satu pun baris log")
    void writesHeaderOnEmptyResult() {
        when(downloadLogRepository.search(
                any(LocalDateTime.class), any(LocalDateTime.class), any(), any(),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        String csv = service.exportCsv(null, null, null, null);

        assertThat(csv).isEqualTo(
                "waktu,jenis_akses,nama,email,divisi,dataset,berkas,format,ukuran_byte,channel,persetujuan,ip\n");
    }

    @Test
    @DisplayName("ekspor dicatat di jejak audit")
    void recordsAuditTrail() {
        exportWithFileName("laporan.csv");

        org.mockito.Mockito.verify(auditLogService).record(
                eq(null), any(), eq("log"), eq("download"), eq("Log unduhan"), any());
    }
}
