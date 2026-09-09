package id.co.erdigma.satudata.modules.dataset.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.HrisPermissionLevel;
import id.co.erdigma.satudata.enums.Role;
import id.co.erdigma.satudata.exception.AccessNotAllowedException;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.division.entity.Division;

/**
 * Mengunci satu-satunya tempat yang menentukan sejauh mana seorang admin boleh
 * melihat dan mengelola isi panel admin.
 *
 * <h2>Kenapa kelas ini ada</h2>
 *
 * Aturan otorisasi punya arah kegagalan yang tidak simetris. Kalau ia terlalu
 * ketat, orangnya melapor hari itu juga. Kalau ia terlalu longgar,
 * <b>tidak ada yang melapor sama sekali</b>: panelnya terbuka, datanya tampil,
 * semuanya terlihat berjalan. Yang berubah cuma siapa yang bisa melihat dan
 * menghapus dataset divisi lain.
 *
 * Karena itu yang dijaga di sini terutama arah yang longgar: nilai kembali
 * {@code null} yang berarti "tidak usah disaring" tidak boleh sampai keluar
 * untuk orang yang bukan admin HRIS.
 */
class AdminDivisionScopeTest {

    private final AdminDivisionScope scope = new AdminDivisionScope();

    private static Division division(UUID id) {
        Division d = new Division();
        d.setId(id);
        return d;
    }

    private static User staffOf(Division d) {
        User u = new User();
        u.setRole(Role.ADMIN);
        u.setHrisPermissionLevel(HrisPermissionLevel.STAFF);
        u.setDivision(d);
        return u;
    }

    private static Dataset datasetOf(Division d) {
        Dataset dataset = new Dataset();
        dataset.setDivision(d);
        return dataset;
    }

    @Test
    @DisplayName("admin biasa disaring ke divisinya sendiri")
    void ordinaryAdminIsScopedToOwnDivision() {
        UUID id = UUID.randomUUID();

        assertThat(scope.filterDivisionId(staffOf(division(id)))).isEqualTo(id);
    }

    /*
      Inti kelas ini.

      null berarti "tidak usah disaring". Kalau ia bocor ke orang yang bukan
      admin HRIS, seluruh pembatasan divisi lenyap tanpa satu pun galat.
    */
    @Test
    @DisplayName("hanya admin HRIS yang tidak disaring sama sekali")
    void onlyHrisAdminSeesEveryDivision() {
        User hrisAdmin = staffOf(division(UUID.randomUUID()));
        hrisAdmin.setHrisPermissionLevel(HrisPermissionLevel.ADMIN);

        assertThat(scope.filterDivisionId(hrisAdmin)).isNull();
        assertThat(scope.seesEveryDivision(hrisAdmin)).isTrue();
    }

    /*
      Peran portal ADMIN bisa ditunjuk dari panel pengguna, sedangkan tingkat
      izin HRIS hanya datang dari HRIS. Kalau yang dibaca peran portal, seorang
      admin bisa menunjuk admin baru yang seketika melihat seluruh divisi, dan
      pembatasan ini kehilangan artinya dalam satu klik.
    */
    @Test
    @DisplayName("peran portal ADMIN tidak membuka seluruh divisi")
    void portalAdminRoleIsNotAShortcut() {
        UUID id = UUID.randomUUID();
        User portalAdmin = staffOf(division(id));
        portalAdmin.setHrisPermissionLevel(HrisPermissionLevel.MANAGER);

        assertThat(scope.seesEveryDivision(portalAdmin)).isFalse();
        assertThat(scope.filterDivisionId(portalAdmin)).isEqualTo(id);
    }

    /*
      Ditolak, BUKAN diloloskan sebagai "tidak ada penyaring".

      Divisi datang dari HRIS dan pemetaannya bisa gagal, misalnya saat team
      seseorang belum terdaftar. Memperlakukan divisi yang kosong sebagai
      "tidak usah disaring" persis membalik maksudnya: yang paling tidak
      terhubung justru melihat paling banyak.
    */
    @Test
    @DisplayName("admin tanpa divisi ditolak, bukan diperlakukan sebagai tanpa penyaring")
    void adminWithoutDivisionIsRejected() {
        assertThatThrownBy(() -> scope.filterDivisionId(staffOf(null)))
                .isInstanceOf(AccessNotAllowedException.class)
                .hasMessageContaining("divisi");
    }

    @Test
    @DisplayName("sesi tanpa pengguna ditolak")
    void missingActorIsRejected() {
        assertThatThrownBy(() -> scope.filterDivisionId(null))
                .isInstanceOf(AccessNotAllowedException.class);
        assertThat(scope.seesEveryDivision(null)).isFalse();
    }

    @Test
    @DisplayName("dataset divisi sendiri boleh dikelola")
    void ownDatasetIsManageable() {
        Division d = division(UUID.randomUUID());

        assertThatCode(() -> scope.assertCanManage(staffOf(d), datasetOf(d)))
                .doesNotThrowAnyException();
    }

    /*
      Yang sesungguhnya membatasi, bukan penyaringan daftarnya.

      Menyaring daftar hanya membuat dataset divisi lain tidak terlihat.
      Alamatnya tetap bisa ditebak dari slug, jadi tanpa pemeriksaan ini
      daftar yang disaring bukan pembatasan melainkan penyamaran.
    */
    @Test
    @DisplayName("dataset divisi lain tidak bisa dikelola walau alamatnya diketahui")
    void otherDivisionDatasetIsRefused() {
        User admin = staffOf(division(UUID.randomUUID()));
        Dataset milikOrangLain = datasetOf(division(UUID.randomUUID()));

        assertThatThrownBy(() -> scope.assertCanManage(admin, milikOrangLain))
                .isInstanceOf(AccessNotAllowedException.class)
                .hasMessageContaining("divisi lain");
    }

    /*
      Dataset tanpa divisi tidak boleh menjadi celah.

      Kolom divisi wajib diisi saat menerbitkan, tetapi data lama atau data
      hasil impor bisa saja kosong. Kalau yang kosong dianggap cocok dengan
      siapa pun, satu baris seperti itu bisa dikelola seluruh admin.
    */
    @Test
    @DisplayName("dataset tanpa divisi tidak cocok dengan admin mana pun")
    void datasetWithoutDivisionMatchesNobody() {
        User admin = staffOf(division(UUID.randomUUID()));

        assertThatThrownBy(() -> scope.assertCanManage(admin, datasetOf(null)))
                .isInstanceOf(AccessNotAllowedException.class);
    }

    @Test
    @DisplayName("admin HRIS boleh mengelola dataset divisi mana pun")
    void hrisAdminManagesAnyDivision() {
        User hrisAdmin = staffOf(division(UUID.randomUUID()));
        hrisAdmin.setHrisPermissionLevel(HrisPermissionLevel.ADMIN);

        assertThatCode(() -> scope.assertCanManage(hrisAdmin, datasetOf(division(UUID.randomUUID()))))
                .doesNotThrowAnyException();
    }
}
