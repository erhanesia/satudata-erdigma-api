package id.co.erdigma.satudata.modules.dataset.helper;

import org.springframework.stereotype.Component;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.Role;
import id.co.erdigma.satudata.exception.AccessNotAllowedException;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;

/**
 * Satu-satunya tempat aturan "siapa boleh melihat dataset ini" ditulis.
 *
 * Dipakai daftar, detail, isi tabel, agregat, dan unduhan. Menyalin aturannya
 * ke lima tempat berarti cepat atau lambat ada satu yang tertinggal saat
 * aturannya berubah — dan yang tertinggal itu adalah kebocoran.
 *
 * <h2>Aturannya</h2>
 *
 * <ol>
 *   <li>Dataset TANPA tag posisi terbuka untuk seluruh karyawan. Portal ini
 *       memang katalog data bersama; membatasi adalah pengecualian yang harus
 *       dinyatakan, bukan keadaan bawaan.</li>
 *   <li>Peran ADMIN melewati pembatasan. Merekalah yang mengelola katalognya;
 *       admin yang tidak bisa membuka apa yang ia atur tidak bisa memeriksa
 *       pekerjaannya sendiri.</li>
 *   <li>Pengunggahnya selalu boleh. Mengunci penerbit dari datanya sendiri
 *       hanya karena ia memberi tag untuk orang lain adalah kejutan yang tidak
 *       ada gunanya.</li>
 *   <li>Selain itu: {@code accessPosition} milik pengguna harus ada di dalam
 *       daftar tag dataset.</li>
 * </ol>
 *
 * <h2>Yang TIDAK dijaga di sini</h2>
 *
 * Angka agregat di {@code /api/v1/stats} tetap menghitung seluruh dataset,
 * termasuk yang tidak boleh dilihat pemanggilnya. Itu keputusan sadar: yang
 * bocor hanya <em>jumlah</em>, bukan judul apalagi isinya, dan membuat angka
 * beranda berbeda-beda per orang membuat portal terasa rusak. Kalau nanti
 * jumlah pun dianggap rahasia, di sinilah tempat mengubahnya.
 */
@Component
public class DatasetAccessGuard {

    public boolean canView(User user, Dataset dataset) {
        if (dataset.getPositions() == null || dataset.getPositions().isEmpty()) {
            return true;
        }
        if (user == null) {
            return false;
        }
        if (user.getRole() == Role.ADMIN) {
            return true;
        }
        if (dataset.getUploadedBy() != null && user.getId() != null
                && user.getId().equals(dataset.getUploadedBy().getId())) {
            return true;
        }
        String position = user.getAccessPosition();
        return position != null && !position.isBlank() && dataset.getPositions().contains(position);
    }

    /**
     * Pesannya sengaja menyebut posisi apa yang dibutuhkan.
     *
     * Menahan keterangan itu tidak membuat siapa pun lebih aman — orangnya
     * sudah masuk sebagai karyawan, dan judul datasetnya sudah ia lihat.
     * Sebaliknya, "403 Forbidden" tanpa penjelasan hanya berakhir jadi tiket
     * ke tim IT yang harus dijawab dengan kalimat yang sama.
     */
    public void assertCanView(User user, Dataset dataset) {
        if (canView(user, dataset)) {
            return;
        }
        throw new AccessNotAllowedException(
                "Dataset \"" + dataset.getTitle() + "\" dibatasi untuk posisi "
                        + String.join(", ", dataset.getPositions())
                        + ". Posisi Anda saat ini: "
                        + (user != null && user.getAccessPosition() != null
                                ? user.getAccessPosition()
                                : "belum diatur")
                        + ".");
    }
}
