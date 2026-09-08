package id.co.erdigma.satudata.modules.download.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public Page<DownloadLogResponse> getAll(int page, int size, LocalDate from, LocalDate to) {
        Pageable pageable = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_SIZE));

        if (from == null && to == null) {
            return downloadLogRepository.findAllByOrderByDownloadedAtDesc(pageable)
                    .map(downloadLogMapper::toResponse);
        }

        // Satu sisi rentang boleh kosong; yang kosong dibuka selebar-lebarnya
        // supaya "dari tanggal ini sampai kapan pun" tetap bisa ditanyakan.
        LocalDateTime start = (from != null) ? from.atStartOfDay() : AWAL_MULA;
        // Batas atasnya akhir hari, bukan awal hari — tanpa ini "sampai hari
        // ini" tidak memuat satu pun unduhan hari ini.
        LocalDateTime end = (to != null) ? to.plusDays(1).atStartOfDay() : LocalDate.now().plusDays(1).atStartOfDay();

        if (end.isBefore(start)) {
            throw new BusinessValidationException(
                    "Tanggal akhir mendahului tanggal awal.");
        }

        return downloadLogRepository
                .findAllByDownloadedAtBetweenOrderByDownloadedAtDesc(start, end, pageable)
                .map(downloadLogMapper::toResponse);
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
    public String exportCsv(User actor, LocalDate from, LocalDate to) {
        LocalDateTime start = (from != null) ? from.atStartOfDay() : AWAL_MULA;
        LocalDateTime end = (to != null) ? to.plusDays(1).atStartOfDay()
                : LocalDate.now().plusDays(1).atStartOfDay();

        if (end.isBefore(start)) {
            throw new BusinessValidationException("Tanggal akhir mendahului tanggal awal.");
        }

        List<DownloadLog> rows = downloadLogRepository
                .findAllByDownloadedAtBetweenOrderByDownloadedAtDesc(start, end,
                        PageRequest.of(0, MAX_EXPORT))
                .getContent();

        StringBuilder csv = new StringBuilder();
        csv.append("waktu,jenis_akses,nama,email,divisi,dataset,berkas,ukuran_byte,")
                .append("channel,persetujuan,ip\n");
        for (DownloadLog l : rows) {
            csv.append(columns(l.getDownloadedAt() == null ? "" : l.getDownloadedAt().toString()))
                    .append(',').append(columns(l.getAccessType()))
                    .append(',').append(columns(l.getUserName()))
                    .append(',').append(columns(l.getUserEmail()))
                    .append(',').append(columns(l.getDivisionCode()))
                    .append(',').append(columns(l.getDatasetSlug()))
                    .append(',').append(columns(l.getFileName()))
                    .append(',').append(l.getSizeBytes())
                    .append(',').append(columns(l.getChannel()))
                    .append(',').append(l.isAgreementAccepted() ? "Disetujui" : "Tidak")
                    .append(',').append(columns(l.getIpAddress()))
                    .append('\n');
        }

        auditLogService.record(actor, AuditAction.CREATE, "log", "download",
                "Log unduhan",
                "Mengekspor " + rows.size() + " baris log unduhan"
                        + (from == null && to == null ? "." : " (" + start.toLocalDate() + " s.d. "
                                + end.toLocalDate().minusDays(1) + ")."));

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
