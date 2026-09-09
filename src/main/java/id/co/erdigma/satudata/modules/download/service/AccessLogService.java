package id.co.erdigma.satudata.modules.download.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetResource;
import id.co.erdigma.satudata.modules.download.entity.DownloadLog;
import id.co.erdigma.satudata.modules.download.repository.DownloadLogRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Satu-satunya tempat yang menulis baris ke {@code download_log}.
 *
 * <h2>Kenapa dikumpulkan di sini</h2>
 *
 * Sebelumnya tiap jalur menulis barisnya sendiri: pratinjau di
 * {@code PreviewService}, unduhan di {@code DownloadService}, dan jalur tabel
 * tidak menulis apa-apa sama sekali. Tiga tempat, tiga salinan penyusunan baris
 * yang sama, dan satu jalur yang terlewat tanpa ada yang menyadarinya.
 *
 * Aturan pembatasan di bawah membuat penyatuan itu wajib, bukan sekadar rapi:
 * aturan yang tersebar di tiga tempat akan berbeda di salah satunya, dan
 * bedanya baru ketahuan saat seseorang membandingkan jumlah baris.
 *
 * <h2>Dua peristiwa, dua aturan yang berbeda</h2>
 *
 * <ol>
 *   <li><b>Membuka dataset</b> dicatat SEKALI SEHARI per orang per dataset.
 *       Yang ingin diketahui "siapa saja yang melihat dataset ini hari ini",
 *       bukan berapa kali ia menyegarkan halamannya. Tanpa batas ini, satu
 *       orang yang membiarkan tab terbuka menulis puluhan baris yang
 *       menenggelamkan baris unduhan yang justru berarti.</li>
 *   <li><b>Mengunduh</b> dicatat SETIAP KALI, tanpa batas harian, karena tiap
 *       unduhan memang peristiwa tersendiri. Tetapi satu AKSI hanya menghasilkan
 *       satu baris, walau aksi itu mengambil beberapa berkas sekaligus.</li>
 * </ol>
 */
@Service
@Slf4j
public class AccessLogService {

    @Autowired
    private DownloadLogRepository downloadLogRepository;

    public static final String OPEN = "PREVIEW";
    public static final String DOWNLOAD = "DOWNLOAD";


    /** Muat kolomnya; lihat changeset 52. */
    private static final int MAX_FORMATS_LENGTH = 200;

    /**
     * Zona yang menentukan kapan "hari" berganti.
     *
     * Dipatok, TIDAK mengikuti jam server, dan itu perbedaan yang nyata:
     * container produksi berjalan di UTC, jadi tengah malamnya jatuh pukul
     * 07:00 WIB. Dengan jam server, orang yang membuka dataset pukul 06:00
     * lalu membukanya lagi pukul 08:00 tercatat DUA KALI, karena di antara
     * keduanya ada pergantian hari yang tidak dialami siapa pun di sini.
     *
     * Yang diminta "sekali sehari", dan hari yang dimaksud hari orang yang
     * memakai aplikasinya.
     */
    private static final ZoneId WIB = ZoneId.of("Asia/Jakarta");

    /**
     * Mencatat bahwa seseorang membuka sebuah dataset.
     *
     * Dipanggil dari setiap jalur yang berarti "orang ini melihat isi dataset
     * ini": halaman detailnya, tabel Data Explorer, dan pratinjau dokumen.
     * Ketiganya lewat sini, dan pembatasan harian yang membuat pemanggilan
     * berulang itu tidak berakibat apa-apa. Jadi menambahkan pemanggilan di
     * jalur baru selalu aman, dan melupakannya yang berbahaya.
     */
    @Transactional
    public void recordOpen(User user, Dataset dataset, String ipAddress, String userAgent) {
        if (user == null || dataset == null) {
            return;
        }
        if (!findRecent(user, dataset, OPEN, awalHariWib()).isEmpty()) {
            return;
        }

        DownloadLog entry = baseEntry(user, dataset, ipAddress, userAgent);
        entry.setAccessType(OPEN);
        // Membuka dataset tidak menyentuh berkas mana pun, jadi tidak ada nama,
        // ukuran, maupun format yang bisa disebut tanpa mengarang.
        entry.setAgreementAccepted(false);
        downloadLogRepository.save(entry);
    }

    /**
     * Tengah malam WIB, dinyatakan dalam jam dinding yang dipakai server.
     *
     * Dua penerjemahan sekaligus, dan keduanya perlu. Yang pertama menentukan
     * hari yang mana: hari menurut orang Indonesia, bukan menurut UTC. Yang
     * kedua menerjemahkannya ke satuan yang sama dengan isi kolom
     * {@code downloaded_at}, yang menyimpan jam dinding server tanpa zona.
     *
     * Ditulis relatif terhadap {@code systemDefault()}, bukan terhadap UTC
     * secara langsung, supaya tetap benar di dua tempat sekaligus: server
     * produksi yang UTC, dan laptop pengembang yang WIB.
     */
    private static LocalDateTime awalHariWib() {
        return ZonedDateTime.now(WIB)
                .toLocalDate()
                .atStartOfDay(WIB)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    /**
     * Mencatat satu berkas yang diunduh, digabungkan bila masih satu aksi.
     *
     * Pemanggilnya tetap memanggil sekali per berkas seperti sebelumnya, dan
     * tidak perlu tahu apa-apa soal penggabungan. Yang menentukan apakah
     * barisnya baru atau menumpang pada baris sebelumnya cuma jarak waktunya.
     */
    @Transactional
    public void recordDownload(User user, Dataset dataset, DatasetResource resource,
            UUID actionId, String ipAddress, String userAgent) {
        if (user == null || dataset == null || resource == null) {
            return;
        }

        String format = formatOf(resource);

        /*
          Digabung HANYA kalau penandanya sama. Tidak ada jeda waktu sama
          sekali.

          Dua penekanan tombol yang berjarak sepersepuluh ribu detik tetap dua
          baris, karena penandanya berbeda. Dan satu penekanan tombol yang
          berkasnya besar sehingga permintaannya terpaut semenit tetap satu
          baris, karena penandanya sama.

          Pemanggil yang tidak menyertakan penanda, misalnya skrip yang
          memanggil API langsung, mendapat satu baris per berkas. Itu memang
          keadaan sebenarnya: tidak ada yang menyatakan panggilan-panggilan itu
          satu peristiwa.
        */
        if (actionId != null) {
            var sebelumnya = downloadLogRepository
                    .findFirstByActionIdAndCognitoIdAndDatasetId(
                            actionId, user.getCognitoId(), dataset.getId());
            if (sebelumnya.isPresent()) {
                merge(sebelumnya.get(), resource, format);
                return;
            }
        }

        DownloadLog entry = baseEntry(user, dataset, ipAddress, userAgent);
        entry.setAccessType(DOWNLOAD);
        entry.setResourceId(resource.getId());
        entry.setFileName(resource.getFileName());
        entry.setSizeBytes(resource.getSizeBytes());
        entry.setFormats(format);
        entry.setActionId(actionId);
        // Sampai di sini berarti persetujuan sudah diperiksa dan diberikan;
        // DownloadService menolak permintaannya sebelum memanggil kita.
        entry.setAgreementAccepted(true);
        downloadLogRepository.save(entry);
    }

    /**
     * Menambahkan satu berkas ke baris unduhan yang sudah ada.
     *
     * {@code resourceId} DIKOSONGKAN begitu barisnya mewakili lebih dari satu
     * berkas. Membiarkannya menunjuk berkas pertama membuat baris itu berbohong
     * tentang dua sisanya, dan kebohongan seperti itu tidak pernah ketahuan
     * karena tidak ada yang gagal.
     */
    private void merge(DownloadLog entry, DatasetResource resource, String format) {
        entry.setResourceId(null);
        entry.setFileName(join(entry.getFileName(), resource.getFileName(), "; ", 255));
        entry.setSizeBytes(entry.getSizeBytes() + resource.getSizeBytes());
        entry.setFormats(joinDistinct(entry.getFormats(), format));
        downloadLogRepository.save(entry);
    }

    private List<DownloadLog> findRecent(User user, Dataset dataset, String accessType,
            LocalDateTime since) {
        return downloadLogRepository.findRecent(user.getCognitoId(), dataset.getId(),
                accessType, since, PageRequest.of(0, 1));
    }

    private DownloadLog baseEntry(User user, Dataset dataset, String ipAddress,
            String userAgent) {
        DownloadLog entry = new DownloadLog();
        entry.setCognitoId(user.getCognitoId());
        entry.setUserName(user.getName());
        entry.setUserEmail(user.getEmail());
        entry.setDivisionCode(user.getDivision() != null ? user.getDivision().getCode() : null);
        entry.setDatasetId(dataset.getId());
        entry.setDatasetSlug(dataset.getSlug());
        entry.setIpAddress(ipAddress);
        entry.setUserAgent(userAgent);
        return entry;
    }

    private static String formatOf(DatasetResource resource) {
        return resource.getFormat() == null ? null : resource.getFormat().getName();
    }

    /**
     * Menggabungkan format tanpa mengulang yang sudah ada.
     *
     * Mengunduh dua CSV sekaligus menghasilkan "CSV", bukan "CSV, CSV". Yang
     * ingin dibaca orang jenis apa saja yang terbawa, bukan berapa berkasnya;
     * jumlah berkasnya sudah terbaca dari kolom nama berkas.
     */
    private static String joinDistinct(String existing, String tambahan) {
        if (tambahan == null || tambahan.isBlank()) {
            return existing;
        }
        Set<String> semua = new LinkedHashSet<>();
        if (existing != null && !existing.isBlank()) {
            Arrays.stream(existing.split(","))
                    .map(String::trim)
                    .filter(f -> !f.isEmpty())
                    .forEach(semua::add);
        }
        semua.add(tambahan.trim());
        return potong(String.join(", ", semua), MAX_FORMATS_LENGTH);
    }

    private static String join(String existing, String tambahan, String pemisah, int batas) {
        if (tambahan == null || tambahan.isBlank()) {
            return existing;
        }
        if (existing == null || existing.isBlank()) {
            return potong(tambahan, batas);
        }
        return potong(existing + pemisah + tambahan, batas);
    }

    /**
     * Memotong pada batas kolomnya.
     *
     * Kolomnya berukuran tetap, dan seseorang yang mengunduh dua puluh berkas
     * sekaligus bisa melampauinya. Dipotong di sini, bukan dibiarkan sampai
     * database menolaknya: kegagalan menulis log TIDAK BOLEH menggagalkan
     * unduhan yang sudah sah, dan orang yang mengunduh tidak pantas melihat
     * galat karena panjang sebuah kolom pencatatan.
     */
    private static String potong(String nilai, int batas) {
        return nilai.length() <= batas ? nilai : nilai.substring(0, batas);
    }
}
