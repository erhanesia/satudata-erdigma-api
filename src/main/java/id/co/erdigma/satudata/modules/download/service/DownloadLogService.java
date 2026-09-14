package id.co.erdigma.satudata.modules.download.service;

import java.util.UUID;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.modules.dataset.helper.AdminDivisionScope;
import id.co.erdigma.satudata.exception.BusinessValidationException;
import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.AuditAction;
import id.co.erdigma.satudata.modules.audit.service.AuditLogService;
import id.co.erdigma.satudata.modules.download.dto.DownloadLogResponse;
import id.co.erdigma.satudata.modules.download.entity.DownloadLog;
import id.co.erdigma.satudata.modules.download.mapper.DownloadLogMapper;
import id.co.erdigma.satudata.modules.download.repository.DownloadLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sisi BACA tabel {@code download_log}.
 *
 * Terpisah dari {@code DownloadService}, yang tugasnya menjalankan pengunduhan
 * dan menulis jejaknya. Menyatukan keduanya berarti satu kelas memegang jalur
 * yang mengeluarkan berkas rahasia sekaligus jalur yang dibaca panel admin —
 * dua kewenangan yang lebih baik tidak berdekatan.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DownloadLogService {

    @Autowired
    private DownloadLogRepository downloadLogRepository;
    @Autowired
    private AdminDivisionScope adminScope;
    @Autowired
    private DownloadLogMapper downloadLogMapper;
    @Autowired
    private AuditLogService auditLogService;

    /** Batas bawah bila rentangnya dibuka tanpa tanggal awal. */
    private static final LocalDateTime AWAL_MULA = LocalDate.of(2000, 1, 1).atStartOfDay();

    private static final int MAX_SIZE = 200;

    /**
     * Batas baris yang boleh keluar dalam satu ekspor.
     *
     * Bukan batas teknis melainkan batas kehati-hatian: berkasnya memuat nama,
     * email, dan alamat IP karyawan. Ekspor tanpa batas membuat seluruh isi
     * tabel bisa dibawa keluar dalam satu klik, dan batas yang terlihat memaksa
     * rentang tanggalnya dipersempit lebih dulu.
     */
    private static final int MAX_EXPORT = 50_000;

    @Transactional(readOnly = true)
    public Page<DownloadLogResponse> getAll(User actor, int page, int size, LocalDate from,
            LocalDate to, String accessType) {
        Pageable pageable = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_SIZE));

        /*
          Batas divisi dipasang di sini, bukan di controller.

          Endpoint ini sudah hanya untuk ADMIN, jadi tidak ada pembaca lain yang
          perlu dibedakan. Menaruh batasnya di lapisan yang menyusun query
          berarti tidak ada jalan menuju data ini yang bisa melewatinya, termasuk
          jalan yang ditambahkan orang lain nanti.
        */
        UUID divisionId = adminScope.filterDivisionId(actor);

        return downloadLogRepository
                .search(awalDari(from), akhirDari(to), normalizeAccessType(accessType),
                        divisionId, pageable)
                .map(downloadLogMapper::toResponse);
    }

    /**
     * Batas bawah rentang, dibuka selebar-lebarnya bila tanggalnya tidak diisi.
     *
     * Sisi yang kosong TIDAK berarti "tidak ada penyaringan tanggal" melainkan
     * "sampai sejauh apa pun ke belakang", supaya "dari tanggal ini sampai kapan
     * pun" tetap bisa ditanyakan.
     */
    private static LocalDateTime awalDari(LocalDate from) {
        return (from != null) ? from.atStartOfDay() : AWAL_MULA;
    }

    /**
     * Batas atas rentang, dan sengaja AWAL HARI BERIKUTNYA.
     *
     * Query membandingkannya dengan {@code <}, bukan {@code <=}. Kalau batasnya
     * awal hari yang diminta, "sampai hari ini" tidak memuat satu pun unduhan
     * hari ini, dan tidak ada yang menyadarinya sampai seseorang mencari
     * unduhannya sendiri dan tidak menemukannya.
     */
    private static LocalDateTime akhirDari(LocalDate to) {
        return (to != null) ? to.plusDays(1).atStartOfDay()
                : LocalDate.now().plusDays(1).atStartOfDay();
    }

    /**
     * Memeriksa jenis akses yang diminta penyaring.
     *
     * Kosong berarti "semua", dan itu keadaan yang sah. Yang TIDAK sah nilai
     * yang tidak dikenal: dibiarkan lewat, ia menghasilkan tabel kosong yang
     * terbaca persis seperti "memang tidak ada datanya". Satu salah ketik di
     * URL berubah jadi kesimpulan yang salah tentang isi sistem, tanpa satu pun
     * tanda bahwa ada yang keliru.
     */
    private static String normalizeAccessType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String jenis = value.trim().toUpperCase(Locale.ROOT);
        if (!"DOWNLOAD".equals(jenis) && !"PREVIEW".equals(jenis)) {
            throw new BusinessValidationException(
                    "Jenis akses hanya boleh DOWNLOAD atau PREVIEW.");
        }
        return jenis;
    }

    /**
     * Menyusun seluruh baris dalam rentang menjadi satu berkas CSV.
     *
     * Ekspornya sendiri DICATAT ke jejak audit. Berkas ini memuat data pribadi
     * karyawan, dan "siapa yang membawanya keluar dari sistem" adalah persis
     * pertanyaan yang harus bisa dijawab belakangan — kalau tidak, log yang
     * dibuat untuk menelusuri kebocoran justru jadi jalan paling mudah untuk
     * membuatnya.
     */
    @Transactional
    public String exportCsv(User actor, LocalDate from, LocalDate to, String accessType) {
        LocalDateTime start = awalDari(from);
        LocalDateTime end = akhirDari(to);
        /*
          Penyaring yang sama dengan yang sedang dilihat di layar.

          Kalau ekspornya mengabaikan penyaring, orang yang sedang menyaring
          "hanya yang dibuka" menekan Export lalu mendapat seluruh isi tabel,
          termasuk puluhan ribu baris yang sengaja ia singkirkan. Berkas itu
          memuat nama, email, dan alamat IP, jadi selisihnya bukan sekadar
          merepotkan.
        */
        String jenisAkses = normalizeAccessType(accessType);

        if (end.isBefore(start)) {
            throw new BusinessValidationException("Tanggal akhir mendahului tanggal awal.");
        }

        // Ekspor tunduk pada batas yang sama dengan yang dilihat di layar.
        // Kalau tidak, seorang admin bisa membawa keluar baris divisi lain
        // yang bahkan tidak pernah bisa ia lihat di tabelnya.
        UUID divisionId = adminScope.filterDivisionId(actor);

        List<DownloadLog> rows = downloadLogRepository
                .search(start, end, jenisAkses, divisionId, PageRequest.of(0, MAX_EXPORT))
                .getContent();

        StringBuilder csv = new StringBuilder();
        csv.append("waktu,jenis_akses,nama,email,divisi,dataset,berkas,format,ukuran_byte,")
                .append("channel,persetujuan,ip\n");
        for (DownloadLog l : rows) {
            csv.append(columns(l.getDownloadedAt() == null ? "" : l.getDownloadedAt().toString()))
                    .append(',').append(columns(l.getAccessType()))
                    .append(',').append(columns(l.getUserName()))
                    .append(',').append(columns(l.getUserEmail()))
                    .append(',').append(columns(l.getDivisionCode()))
                    .append(',').append(columns(l.getDatasetSlug()))
                    .append(',').append(columns(l.getFileName()))
                    .append(',').append(columns(l.getFormats()))
                    .append(',').append(l.getSizeBytes())
                    .append(',').append(columns(l.getChannel()))
                    .append(',').append(l.isAgreementAccepted() ? "Disetujui" : "Tidak")
                    .append(',').append(columns(l.getIpAddress()))
                    .append('\n');
        }

        /*
          Jejak auditnya menyebutkan penyaring yang dipakai, bukan cuma jumlah
          barisnya. "Mengekspor 42 baris" tidak bisa dibandingkan dengan apa pun
          belakangan; yang bisa ditelusuri adalah 42 baris YANG MANA.
        */
        String keteranganJenis = switch (jenisAkses == null ? "" : jenisAkses) {
            case "PREVIEW" -> " yang dibuka";
            case "DOWNLOAD" -> " yang diunduh";
            default -> "";
        };
        String keteranganRentang = (from == null && to == null) ? ""
                : " (" + start.toLocalDate() + " s.d. " + end.toLocalDate().minusDays(1) + ")";

        auditLogService.record(actor, AuditAction.CREATE, "log", "download",
                "Log unduhan",
                "Mengekspor " + rows.size() + " baris log unduhan"
                        + keteranganJenis + keteranganRentang + ".");

        log.info("Log unduhan diekspor: {} baris oleh {}", rows.size(),
                actor != null ? actor.getCognitoId() : "sistem");
        return csv.toString();
    }

    /**
     * Karakter yang membuat Excel, LibreOffice, dan Google Sheets memperlakukan
     * isi sel sebagai RUMUS, bukan teks.
     *
     * Tab dan carriage return ikut karena keduanya tidak terlihat mata. Nilai
     * yang diawali salah satunya bisa lolos pemeriksaan yang hanya mencari empat
     * karakter pertama, lalu tetap dibaca sebagai rumus setelah spasi awalnya
     * diabaikan.
     */
    private static final String FORMULA_PREFIXES = "=+-@\t\r";

    /**
     * Menyiapkan satu nilai untuk ditulis ke CSV.
     *
     * Dua persoalan berbeda diselesaikan di sini, dan penting untuk tidak
     * menganggapnya satu.
     *
     * <b>Pengutipan CSV</b> menjaga agar koma, tanda kutip, dan baris baru tidak
     * memecah struktur berkasnya. Ini soal format.
     *
     * <b>Netralisasi rumus</b> menjaga agar isi sel tidak dieksekusi aplikasi
     * spreadsheet. Ini soal keamanan, dan pengutipan CSV TIDAK menolong sama
     * sekali: Excel melepas kutipnya lebih dulu, baru membaca isinya, sehingga
     * nilai berkutip pun tetap berakhir sebagai rumus.
     *
     * Jalur masuknya nyata. Nama berkas diambil apa adanya dari unggahan di
     * DatasetUploadService dan hanya diperiksa ekstensinya, lalu ikut tercatat
     * di kolom file_name tabel ini. Berkas yang namanya diawali tanda sama
     * dengan akan lolos, dan rumusnya berjalan di komputer admin yang membuka
     * hasil ekspor, mengirimkan isi sel di sekitarnya: nama, email, dan divisi
     * seluruh pengunduh.
     *
     * Kutip tunggal di depan membuat aplikasi spreadsheet membacanya sebagai
     * teks. Kutip itu tidak ditampilkan di layar, jadi pengguna yang sah tidak
     * melihat perbedaan apa pun.
     *
     * Berlaku juga untuk kasus tanpa niat jahat. Berkas bernama
     * "-rekap-2026.csv" tanpa penjagaan ini akan tampil sebagai #NAME? alih-alih
     * namanya sendiri.
     */
    private String columns(String value) {
        String content = (value == null) ? "" : value;

        if (!content.isEmpty() && FORMULA_PREFIXES.indexOf(content.charAt(0)) >= 0) {
            content = "'" + content;
        }

        if (content.indexOf(',') < 0 && content.indexOf('"') < 0 && content.indexOf('\n') < 0) {
            return content;
        }
        return '"' + content.replace("\"", "\"\"") + '"';
    }
}
