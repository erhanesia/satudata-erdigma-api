package id.co.erdigma.satudata.modules.dataset.helper;

import java.util.UUID;

import org.springframework.stereotype.Component;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.HrisPermissionLevel;
import id.co.erdigma.satudata.exception.AccessNotAllowedException;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;

/**
 * Sejauh mana seorang admin boleh melihat dan mengelola isi panel admin.
 *
 * <h2>Aturannya satu kalimat</h2>
 *
 * Admin hanya berurusan dengan dataset divisinya sendiri, kecuali admin HRIS
 * yang berurusan dengan semuanya.
 *
 * Divisi di sini adalah <b>team</b> di HRIS; jembatannya kolom
 * {@code hrisTeamId} pada tabel divisi. Dan dataset selalu mewarisi divisi
 * pengunggahnya saat diterbitkan, jadi "dataset divisi saya" dan "dataset yang
 * diunggah orang divisi saya" adalah hal yang sama, bukan dua hal yang kebetulan
 * sering berimpit.
 *
 * <h2>Kenapa terkumpul di satu kelas</h2>
 *
 * Aturan yang sama harus berlaku di enam tempat: daftar dataset, penyuntingan,
 * penghapusan, log akses, jejak audit, dan angka dasbor. Aturan otorisasi yang
 * disalin ke enam tempat akan berbeda di salah satunya, dan yang berbeda itu
 * justru tidak akan ketahuan, karena tidak ada yang gagal saat seseorang
 * melihat lebih banyak daripada yang seharusnya.
 *
 * <h2>Bedanya dengan {@link DatasetAccessGuard}</h2>
 *
 * Keduanya soal izin, tetapi menjawab pertanyaan yang berbeda dan berlaku di
 * tempat yang berbeda:
 *
 * <ul>
 *   <li>{@code DatasetAccessGuard} menjawab <b>siapa boleh MELIHAT</b> sebuah
 *       dataset di portal, menurut aturan akses yang dipasang penerbitnya.
 *       Berlaku untuk seluruh karyawan.</li>
 *   <li>Kelas ini menjawab <b>siapa boleh MENGELOLA</b>-nya di panel admin,
 *       menurut divisi. Berlaku hanya untuk admin.</li>
 * </ul>
 *
 * Keduanya tidak saling menggantikan. Seorang admin bisa saja berhak melihat
 * dataset divisi lain di portal, dan tetap tidak boleh menyuntingnya.
 */
@Component
public class AdminDivisionScope {

    /**
     * Divisi yang dipakai menyaring, atau {@code null} bila tidak perlu
     * menyaring sama sekali.
     *
     * <h2>Kenapa satu metode, bukan dua</h2>
     *
     * Godaan yang jelas adalah memisahkannya menjadi "apakah dia admin HRIS"
     * dan "divisinya apa". Bentuk itu berbahaya: pemanggil yang lupa memeriksa
     * yang pertama akan menyaring admin HRIS ke divisinya sendiri, atau lebih
     * buruk, memperlakukan divisi yang kosong sebagai "tidak perlu menyaring"
     * dan membuka seluruh katalog kepada orang yang tidak berhak.
     *
     * Satu metode dengan satu nilai kembali membuat kedua keadaan itu tidak
     * bisa tertukar.
     *
     * <h2>Admin tanpa divisi ditolak, bukan dibiarkan melihat kosong</h2>
     *
     * Divisi datang dari HRIS, dan pemetaannya bisa gagal, misalnya saat team
     * seseorang belum terdaftar. Membiarkannya lewat sebagai "tidak ada
     * penyaring" berarti ia melihat segalanya, yaitu kebalikan dari yang
     * dimaksud.
     *
     * Membiarkannya melihat nol baris juga bukan jawaban yang baik: yang tampil
     * panel kosong tanpa sebab, dan orangnya akan melapor bahwa datanya hilang.
     * Menolak dengan pesan yang menyebutkan sebabnya lebih jujur, dan ia memang
     * tidak akan bisa menerbitkan apa pun juga, karena kolom divisi pada dataset
     * wajib diisi.
     */
    public UUID filterDivisionId(User admin) {
        if (admin == null) {
            throw new AccessNotAllowedException(
                    "Sesi tidak dikenali. Silakan masuk kembali.");
        }
        if (seesEveryDivision(admin)) {
            return null;
        }

        UUID divisionId = admin.getDivisionId();
        if (divisionId == null) {
            throw new AccessNotAllowedException(
                    "Akun Anda belum terhubung ke divisi mana pun di HRIS, jadi belum ada "
                            + "dataset yang bisa dikelola. Hubungi HR untuk melengkapi data team "
                            + "Anda.");
        }
        return divisionId;
    }

    /**
     * Admin HRIS berurusan dengan seluruh divisi.
     *
     * Diperiksa lewat tingkat izin HRIS, BUKAN lewat peran portal. Keduanya
     * sama-sama bernama ADMIN dan itu memang membingungkan, tetapi asalnya
     * berbeda dan itulah intinya: peran portal ditunjuk dari panel ini sendiri,
     * sedangkan tingkat izin HRIS hanya bisa datang dari HRIS.
     *
     * Kalau yang dipakai peran portal, seorang admin bisa menunjuk admin baru
     * yang seketika melihat seluruh divisi, dan pembatasan ini kehilangan
     * artinya dalam satu klik.
     */
    public boolean seesEveryDivision(User admin) {
        return admin != null && admin.getHrisPermissionLevel() == HrisPermissionLevel.ADMIN;
    }

    /**
     * Menolak bila admin ini tidak berhak mengelola dataset tersebut.
     *
     * <h2>Kenapa ini yang sesungguhnya, bukan penyaringan daftarnya</h2>
     *
     * Menyaring daftar hanya membuat dataset divisi lain tidak terlihat.
     * Alamatnya tetap bisa ditebak dari slug, dan sebelum ada pemeriksaan ini
     * admin divisi mana pun bisa menyunting atau menghapus dataset divisi lain
     * hanya dengan mengetik alamatnya.
     *
     * Daftar yang disaring tanpa pemeriksaan ini bukan pembatasan, melainkan
     * penyamaran.
     */
    public void assertCanManage(User admin, Dataset dataset) {
        UUID scope = filterDivisionId(admin);
        if (scope == null) {
            /*
              Admin HRIS lolos SEBELUM divisi datasetnya diperiksa, dan itu
              disengaja.

              Kolom divisi pada dataset bertanda NOT NULL, di entity maupun di
              skema, jadi dataset tanpa divisi memang tidak bisa terbentuk.
              Tetapi seandainya suatu saat muncul, misalnya lewat bug migrasi
              atau impor, admin HRIS-lah satu-satunya yang bisa membereskannya.

              Menolaknya di sini akan mengubah anomali data yang bisa diperbaiki
              menjadi baris yang terkunci permanen: tidak bisa disunting, tidak
              bisa dihapus, tidak bisa diperbaiki oleh siapa pun.
            */
            return;
        }

        // Pemeriksaan null di bawah ini jaring pengaman, bukan tanda bahwa null
        // mungkin terjadi. Lihat alasannya di atas.
        UUID pemilik = (dataset.getDivision() != null) ? dataset.getDivision().getId() : null;
        if (!scope.equals(pemilik)) {
            /*
              Pesannya sengaja tidak menyebut dataset itu ada atau tidak.

              Membedakan "tidak ada" dari "ada tapi bukan milik Anda" memberi
              tahu orang luar bahwa sebuah dataset dengan slug itu memang ada,
              dan slug dibentuk dari judulnya. Judul dataset divisi lain bisa
              menerangkan hal yang seharusnya tidak ia ketahui.
            */
            throw new AccessNotAllowedException(
                    "Dataset ini milik divisi lain, jadi tidak bisa dikelola dari akun Anda.");
        }
    }
}
