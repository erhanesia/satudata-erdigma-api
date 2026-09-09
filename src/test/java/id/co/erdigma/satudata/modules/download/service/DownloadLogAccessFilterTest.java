package id.co.erdigma.satudata.modules.download.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import id.co.erdigma.satudata.exception.BusinessValidationException;
import id.co.erdigma.satudata.modules.audit.service.AuditLogService;
import id.co.erdigma.satudata.modules.download.entity.DownloadLog;
import id.co.erdigma.satudata.modules.download.mapper.DownloadLogMapper;
import id.co.erdigma.satudata.modules.download.repository.DownloadLogRepository;

/**
 * Mengunci penyaring jenis akses pada halaman Log.
 *
 * <h2>Kenapa kelas ini ada</h2>
 *
 * Penyaring punya sifat yang membuatnya berbahaya untuk dibiarkan tanpa tes:
 * <b>kegagalannya tidak pernah berupa galat.</b> Penyaring yang diam-diam tidak
 * diteruskan ke query menghasilkan tabel yang terlihat wajar, terisi, dan
 * berurutan. Yang salah cuma isinya, dan tidak ada satu pun tanda di layar.
 *
 * <h2>Dua hal yang dijaga, dan yang kedua bukan soal kerapian</h2>
 *
 * <ol>
 *   <li><b>Nilai yang tidak dikenal ditolak.</b> Dibiarkan lewat, ia
 *       menghasilkan tabel kosong yang terbaca persis seperti "memang tidak ada
 *       datanya". Satu salah ketik di URL berubah menjadi kesimpulan yang salah
 *       tentang isi sistem.</li>
 *   <li><b>Ekspor CSV memakai penyaring yang sama dengan layar.</b> Kalau
 *       ekspornya mengabaikan penyaring, admin yang sedang melihat belasan baris
 *       pratinjau menekan Export lalu membawa keluar puluhan ribu baris berisi
 *       nama, email, dan alamat IP karyawan yang justru sengaja ia singkirkan.
 *       Selisihnya bukan sekadar merepotkan.</li>
 * </ol>
 */
class DownloadLogAccessFilterTest {

    private final DownloadLogRepository downloadLogRepository = mock(DownloadLogRepository.class);
    private final DownloadLogMapper downloadLogMapper = mock(DownloadLogMapper.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final DownloadLogService service = new DownloadLogService();

    @BeforeEach
    void wireFields() {
        ReflectionTestUtils.setField(service, "downloadLogRepository", downloadLogRepository);
        ReflectionTestUtils.setField(service, "downloadLogMapper", downloadLogMapper);
        ReflectionTestUtils.setField(service, "auditLogService", auditLogService);

        Page<DownloadLog> empty = new PageImpl<>(List.of());
        when(downloadLogRepository.search(any(), any(), any(), any())).thenReturn(empty);
    }

    /** Jenis akses yang benar-benar sampai ke query, apa pun bentuk masukannya. */
    private String accessTypeSentToQuery() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(downloadLogRepository).search(
                any(LocalDateTime.class), any(LocalDateTime.class), captor.capture(),
                any(Pageable.class));
        return captor.getValue();
    }

    @Test
    @DisplayName("tanpa penyaring, query menerima null yang berarti semua jenis akses")
    void passesNullWhenNotFiltering() {
        service.getAll(0, 20, null, null, null);

        assertThat(accessTypeSentToQuery()).isNull();
    }

    @Test
    @DisplayName("penyaring kosong diperlakukan sama dengan tidak menyaring")
    void treatsBlankAsNoFilter() {
        service.getAll(0, 20, null, null, "   ");

        assertThat(accessTypeSentToQuery()).isNull();
    }

    @Test
    @DisplayName("huruf kecil dan spasi berlebih dirapikan, bukan ditolak")
    void normalisesCasingAndSpacing() {
        service.getAll(0, 20, null, null, "  download  ");

        assertThat(accessTypeSentToQuery()).isEqualTo("DOWNLOAD");
    }

    @Test
    @DisplayName("PREVIEW diteruskan apa adanya")
    void passesPreviewThrough() {
        service.getAll(0, 20, null, null, "PREVIEW");

        assertThat(accessTypeSentToQuery()).isEqualTo("PREVIEW");
    }

    /*
      Ditolak, BUKAN diabaikan.

      Mengabaikannya berarti mengembalikan seluruh tabel kepada orang yang
      mengira sedang melihat sebagian; menganggapnya penyaring yang tidak cocok
      berarti mengembalikan tabel kosong kepada orang yang mengira sistemnya
      memang kosong. Dua-duanya menjawab pertanyaan yang tidak ditanyakan.
    */
    @Test
    @DisplayName("jenis akses yang tidak dikenal ditolak, bukan diabaikan")
    void rejectsUnknownAccessType() {
        assertThatThrownBy(() -> service.getAll(0, 20, null, null, "SEMBARANG"))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("DOWNLOAD")
                .hasMessageContaining("PREVIEW");
    }

    @Test
    @DisplayName("ekspor CSV menolak jenis akses yang tidak dikenal juga")
    void exportRejectsUnknownAccessType() {
        assertThatThrownBy(() -> service.exportCsv(null, null, null, "SEMBARANG"))
                .isInstanceOf(BusinessValidationException.class);
    }

    /*
      Inti kelas ini.

      Kalau baris ini gagal, artinya penyaring di layar dan penyaring di berkas
      yang diunduh sudah berbeda, dan bedanya berupa data pribadi yang ikut
      terbawa keluar tanpa diminta.
    */
    @Test
    @DisplayName("ekspor CSV memakai penyaring yang sama dengan yang sedang dilihat")
    void exportHonoursTheSameFilter() {
        service.exportCsv(null, null, null, "preview");

        assertThat(accessTypeSentToQuery()).isEqualTo("PREVIEW");
    }
}
