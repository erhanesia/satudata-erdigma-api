package id.co.erdigma.satudata.modules.download.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetResource;
import id.co.erdigma.satudata.modules.dataset.entity.Format;
import id.co.erdigma.satudata.modules.download.entity.DownloadLog;
import id.co.erdigma.satudata.modules.download.repository.DownloadLogRepository;

/**
 * Mengunci dua aturan pencatatan yang kegagalannya tidak pernah berupa galat.
 *
 * <h2>Kenapa kelas ini ada</h2>
 *
 * Kedua aturan di sini soal <b>berapa banyak baris yang ditulis</b>, dan itu
 * jenis perilaku yang rusaknya paling senyap. Pembatasan harian yang tidak
 * berlaku tidak menghasilkan apa pun selain tabel yang lebih penuh, dan
 * penggabungan yang tidak jalan tidak menghasilkan apa pun selain tiga baris
 * alih-alih satu. Tidak ada yang gagal, tidak ada yang merah, dan yang
 * menyadarinya cuma orang yang kebetulan menghitung.
 *
 * Justru itu sebabnya jumlah barisnya harus dikunci di sini, bukan dipercayakan
 * pada seseorang yang nanti membaca ulang kodenya.
 */
class AccessLogServiceTest {

    private final DownloadLogRepository downloadLogRepository = mock(DownloadLogRepository.class);
    private final AccessLogService service = new AccessLogService();

    private User user;
    private Dataset dataset;

    @BeforeEach
    void wireFields() {
        ReflectionTestUtils.setField(service, "downloadLogRepository", downloadLogRepository);

        user = new User();
        user.setCognitoId("cognito-1");
        user.setName("Budi Santoso");
        user.setEmail("budi@erdigma.co.id");

        dataset = new Dataset();
        dataset.setId(UUID.randomUUID());
        dataset.setSlug("penjualan-bulanan");

        belumAdaBarisSebelumnya();
        when(downloadLogRepository.findFirstByActionIdAndCognitoIdAndDatasetId(
                any(), any(), any())).thenReturn(Optional.empty());
    }

    private void belumAdaBarisSebelumnya() {
        when(downloadLogRepository.findRecent(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());
    }

    private void sudahAdaBaris(DownloadLog baris) {
        when(downloadLogRepository.findRecent(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(baris));
    }

    /** Baris yang sudah ada untuk penanda aksi tertentu. */
    private void sudahAdaAksi(UUID actionId, DownloadLog baris) {
        when(downloadLogRepository.findFirstByActionIdAndCognitoIdAndDatasetId(
                eq(actionId), any(), any())).thenReturn(Optional.of(baris));
    }

    private DatasetResource berkas(String nama, String format, long ukuran) {
        Format f = new Format();
        f.setName(format);

        DatasetResource resource = new DatasetResource();
        resource.setId(UUID.randomUUID());
        resource.setFileName(nama);
        resource.setSizeBytes(ukuran);
        resource.setFormat(f);
        return resource;
    }

    private DownloadLog tersimpan() {
        ArgumentCaptor<DownloadLog> captor = ArgumentCaptor.forClass(DownloadLog.class);
        verify(downloadLogRepository).save(captor.capture());
        return captor.getValue();
    }

    // ------------------------------------------------------------ membuka

    @Test
    @DisplayName("membuka dataset pertama kali hari ini menulis satu baris tanpa berkas")
    void firstOpenOfTheDayIsRecorded() {
        service.recordOpen(user, dataset, "10.0.0.1", "Chrome");

        DownloadLog baris = tersimpan();
        assertThat(baris.getAccessType()).isEqualTo("PREVIEW");
        assertThat(baris.getDatasetSlug()).isEqualTo("penjualan-bulanan");
        // Membuka dataset tidak menyentuh berkas mana pun.
        assertThat(baris.getResourceId()).isNull();
        assertThat(baris.getFileName()).isNull();
        assertThat(baris.getFormats()).isNull();
        assertThat(baris.isAgreementAccepted()).isFalse();
    }

    @Test
    @DisplayName("membuka dataset yang sama lagi di hari yang sama tidak menulis apa-apa")
    void secondOpenOnTheSameDayIsSkipped() {
        sudahAdaBaris(new DownloadLog());

        service.recordOpen(user, dataset, "10.0.0.1", "Chrome");

        verify(downloadLogRepository, never()).save(any());
    }

    /*
      Batas bawah pencariannya HARUS awal hari, bukan "24 jam terakhir".

      Bedanya terasa di sekitar tengah malam: dengan jendela bergulir, orang
      yang membuka dataset pukul 23.00 lalu membukanya lagi pukul 01.00 tidak
      tercatat pada hari yang baru sama sekali, dan hari itu kehilangan satu
      pembacanya tanpa jejak apa pun.
    */
    @Test
    @DisplayName("pembatasannya dihitung sejak tengah malam WIB, bukan tengah malam server")
    void dailyCapCountsFromJakartaMidnight() {
        service.recordOpen(user, dataset, "10.0.0.1", "Chrome");

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(downloadLogRepository).findRecent(any(), any(), any(), captor.capture(),
                any(Pageable.class));

        /*
          Diperiksa sebagai TITIK WAKTU, bukan sebagai angka jam.

          Nilai yang dikirim ke query berupa jam dinding server, jadi angkanya
          berbeda di laptop WIB dan di container UTC. Yang harus sama di
          keduanya adalah momen yang ditunjuknya: tengah malam di Jakarta.
        */
        java.time.LocalTime diJakarta = captor.getValue()
                .atZone(java.time.ZoneId.systemDefault())
                .withZoneSameInstant(java.time.ZoneId.of("Asia/Jakarta"))
                .toLocalTime();
        assertThat(diJakarta).isEqualTo(java.time.LocalTime.MIDNIGHT);
    }

    // ----------------------------------------------------------- mengunduh

    @Test
    @DisplayName("unduhan pertama menulis baris baru lengkap dengan formatnya")
    void firstDownloadWritesANewRow() {
        service.recordDownload(user, dataset, berkas("data.csv", "CSV", 100), null,
                "10.0.0.1", "Chrome");

        DownloadLog baris = tersimpan();
        assertThat(baris.getAccessType()).isEqualTo("DOWNLOAD");
        assertThat(baris.getFormats()).isEqualTo("CSV");
        assertThat(baris.getFileName()).isEqualTo("data.csv");
        assertThat(baris.getSizeBytes()).isEqualTo(100);
        assertThat(baris.getResourceId()).isNotNull();
        assertThat(baris.isAgreementAccepted()).isTrue();
    }

    /*
      Inti kelas ini.

      Satu penekanan tombol Unduh pada dataset berisi dua berkas memanggil
      endpoint unduh dua kali. Kalau baris ini gagal, artinya satu peristiwa
      kembali tercatat sebagai dua, dan tabelnya membengkak sebanding dengan
      jumlah berkas di tiap dataset.
    */
    @Test
    @DisplayName("berkas kedua dalam aksi yang sama menumpang, bukan membuat baris baru")
    void secondFileInTheSameActionMergesIn() {
        UUID aksi = UUID.randomUUID();
        DownloadLog sebelumnya = new DownloadLog();
        sebelumnya.setAccessType("DOWNLOAD");
        sebelumnya.setResourceId(UUID.randomUUID());
        sebelumnya.setFileName("data.csv");
        sebelumnya.setSizeBytes(100);
        sebelumnya.setFormats("CSV");
        sebelumnya.setActionId(aksi);
        sudahAdaAksi(aksi, sebelumnya);

        service.recordDownload(user, dataset, berkas("ringkasan.docx", "DOCX", 40), aksi,
                "10.0.0.1", "Chrome");

        DownloadLog baris = tersimpan();
        assertThat(baris).isSameAs(sebelumnya);
        assertThat(baris.getFormats()).isEqualTo("CSV, DOCX");
        assertThat(baris.getFileName()).isEqualTo("data.csv; ringkasan.docx");
        assertThat(baris.getSizeBytes()).isEqualTo(140);
        // Barisnya kini mewakili dua berkas; menunjuk salah satunya berarti
        // berbohong tentang yang lain.
        assertThat(baris.getResourceId()).isNull();
    }

    @Test
    @DisplayName("dua berkas berformat sama tidak menulis formatnya dua kali")
    void repeatedFormatIsNotDuplicated() {
        UUID aksi = UUID.randomUUID();
        DownloadLog sebelumnya = new DownloadLog();
        sebelumnya.setFormats("CSV");
        sebelumnya.setFileName("a.csv");
        sebelumnya.setActionId(aksi);
        sudahAdaAksi(aksi, sebelumnya);

        service.recordDownload(user, dataset, berkas("b.csv", "CSV", 10), aksi,
                "10.0.0.1", "Chrome");

        assertThat(tersimpan().getFormats()).isEqualTo("CSV");
    }

    /*
      Yang menentukan penggabungan HANYA penandanya, bukan jaraknya.

      Versi pertama memakai jendela tiga puluh detik, dan itu salah di kedua
      arah sekaligus. Front-end mengunduh berurutan, jadi berkas besar membuat
      satu aksi terpaut lebih lama dari jendela mana pun yang masuk akal;
      sementara jendela yang cukup longgar untuk itu ikut menelan unduhan
      berikutnya yang benar-benar disengaja. Satu baris sempat memuat empat
      berkas dari tiga aksi berbeda.
    */
    @Test
    @DisplayName("dua aksi berbeda tetap dua baris, sedekat apa pun jaraknya")
    void twoActionsStayTwoRows() {
        service.recordDownload(user, dataset, berkas("data.csv", "CSV", 100),
                UUID.randomUUID(), "10.0.0.1", "Chrome");
        service.recordDownload(user, dataset, berkas("data.csv", "CSV", 100),
                UUID.randomUUID(), "10.0.0.1", "Chrome");

        verify(downloadLogRepository, org.mockito.Mockito.times(2)).save(any());
        // Tidak ada pencarian berbasis waktu sama sekali pada jalur unduh.
        verify(downloadLogRepository, never()).findRecent(any(), any(), eq("DOWNLOAD"),
                any(), any(Pageable.class));
    }

    @Test
    @DisplayName("tanpa penanda aksi, tiap panggilan tercatat sendiri-sendiri")
    void withoutAnActionIdEachCallStandsAlone() {
        service.recordDownload(user, dataset, berkas("a.csv", "CSV", 10), null,
                "10.0.0.1", "Chrome");
        service.recordDownload(user, dataset, berkas("b.docx", "DOCX", 20), null,
                "10.0.0.1", "Chrome");

        verify(downloadLogRepository, org.mockito.Mockito.times(2)).save(any());
    }
}
