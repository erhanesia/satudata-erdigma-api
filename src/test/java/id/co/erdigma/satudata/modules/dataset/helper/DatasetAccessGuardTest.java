package id.co.erdigma.satudata.modules.dataset.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.AccessRuleType;
import id.co.erdigma.satudata.enums.Role;
import id.co.erdigma.satudata.exception.AccessNotAllowedException;
import id.co.erdigma.satudata.modules.dataset.entity.AccessRule;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;

/**
 * Menjaga aturan "siapa boleh melihat" agar tidak diam-diam berubah.
 *
 * Kegagalan di kelas ini bukan soal tampilan. Aturan yang terlalu longgar
 * membocorkan data yang sengaja dibatasi; yang terlalu ketat mengunci orang dari
 * data yang berhak ia lihat, dan itu berakhir jadi tiket ke tim IT alih-alih
 * galat yang terlihat.
 *
 * Tanpa Spring: guard ini murni logika, tidak menyentuh database maupun HRIS.
 * Konteks Spring hanya akan memperlambat tanpa menguji apa pun tambahan.
 */
class DatasetAccessGuardTest {

    private final DatasetAccessGuard guard = new DatasetAccessGuard();

    private static final UUID DATA_MANAGER_POSITION = UUID.randomUUID();
    private static final UUID EMPLOYEE_BUDI = UUID.randomUUID();

    private User userWith(String jobLevel, UUID positionId, UUID employeeId) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(Role.STAFF);
        user.setJobLevel(jobLevel);
        user.setHrisPositionId(positionId);
        user.setHrisEmployeeId(employeeId);
        return user;
    }

    private Dataset datasetWith(AccessRule... rules) {
        Dataset dataset = new Dataset();
        dataset.setId(UUID.randomUUID());
        dataset.setTitle("Laporan Rahasia");
        dataset.setAccessRules(new java.util.ArrayList<>(List.of(rules)));
        return dataset;
    }

    @Test
    @DisplayName("dataset tanpa aturan terbuka untuk seluruh karyawan")
    void openToEveryoneWithoutRules() {
        User anyone = userWith("Staff", null, null);
        assertThat(guard.canView(anyone, datasetWith())).isTrue();
    }

    @Test
    @DisplayName("dataset tanpa aturan pun tertutup bagi yang belum masuk")
    void stillRequiresLogin() {
        // Bukan pengecualian yang tidak konsisten: seluruh endpoint di portal ini
        // memang butuh autentikasi. Yang diuji di sini bahwa guard tidak
        // mengandalkan lapisan di atasnya untuk itu.
        assertThat(guard.canView(null, datasetWith(
                new AccessRule(AccessRuleType.JOB_LEVEL, "Manager")))).isFalse();
    }

    @Test
    @DisplayName("aturan JOB_LEVEL cocok tanpa memedulikan huruf besar-kecil")
    void matchesJobLevelIgnoringCase() {
        Dataset d = datasetWith(new AccessRule(AccessRuleType.JOB_LEVEL, "Senior Manager"));

        assertThat(guard.canView(userWith("Senior Manager", null, null), d)).isTrue();
        // HRIS mengirim bentuk tampilan, tetapi nilai yang tersimpan bisa saja
        // datang dari jalur lain dengan huruf berbeda. Pembatasan akses tidak
        // boleh bergantung pada hal sesepele itu.
        assertThat(guard.canView(userWith("senior manager", null, null), d)).isTrue();
        assertThat(guard.canView(userWith("Manager", null, null), d)).isFalse();
    }

    @Test
    @DisplayName("aturan POSITION dicocokkan lewat UUID, bukan nama")
    void matchesPositionByUuid() {
        Dataset d = datasetWith(new AccessRule(AccessRuleType.POSITION, DATA_MANAGER_POSITION.toString()));

        assertThat(guard.canView(userWith("Staff", DATA_MANAGER_POSITION, null), d)).isTrue();
        assertThat(guard.canView(userWith("Staff", UUID.randomUUID(), null), d)).isFalse();
    }

    @Test
    @DisplayName("aturan EMPLOYEE mengizinkan orang yang ditunjuk meski jenjangnya tidak cocok")
    void namedEmployeeBypassesJobLevel() {
        // Inilah gunanya penunjukan perorangan: seorang Magang yang terlibat satu
        // proyek boleh melihat datanya, tanpa harus membuka dataset itu untuk
        // seluruh Magang di perusahaan.
        Dataset d = datasetWith(
                new AccessRule(AccessRuleType.JOB_LEVEL, "Direktur"),
                new AccessRule(AccessRuleType.EMPLOYEE, EMPLOYEE_BUDI.toString()));

        assertThat(guard.canView(userWith("Magang", null, EMPLOYEE_BUDI), d)).isTrue();
        assertThat(guard.canView(userWith("Magang", null, UUID.randomUUID()), d)).isFalse();
    }

    @Test
    @DisplayName("ketiga sumbu berdiri sejajar, bukan bertingkat")
    void threeAxesAreParallel() {
        // Dataset dengan JOB_LEVEL=Manager DAN EMPLOYEE=Budi terlihat oleh
        // seluruh Manager DAN oleh Budi — bukan hanya oleh Manager yang kebetulan
        // bernama Budi. Kalau suatu saat aturannya diubah jadi AND, tes ini yang
        // pertama gagal.
        Dataset d = datasetWith(
                new AccessRule(AccessRuleType.JOB_LEVEL, "Manager"),
                new AccessRule(AccessRuleType.EMPLOYEE, EMPLOYEE_BUDI.toString()));

        assertThat(guard.canView(userWith("Manager", null, UUID.randomUUID()), d)).isTrue();
        assertThat(guard.canView(userWith("Staff", null, EMPLOYEE_BUDI), d)).isTrue();
    }

    @Test
    @DisplayName("pengguna tanpa pengenal HRIS tidak melihat dataset berbatas")
    void unsyncedUserSeesNothingRestricted() {
        // Keadaan nyata: pengguna Cognito yang baru login dan belum tersinkron
        // punya hrisPositionId dan hrisEmployeeId null. Null TIDAK BOLEH dianggap
        // cocok dengan apa pun — gagal ke arah menutup, bukan membuka.
        User unsynced = userWith(null, null, null);

        assertThat(guard.canView(unsynced, datasetWith(
                new AccessRule(AccessRuleType.JOB_LEVEL, "Manager")))).isFalse();
        assertThat(guard.canView(unsynced, datasetWith(
                new AccessRule(AccessRuleType.POSITION, DATA_MANAGER_POSITION.toString())))).isFalse();
        assertThat(guard.canView(unsynced, datasetWith(
                new AccessRule(AccessRuleType.EMPLOYEE, EMPLOYEE_BUDI.toString())))).isFalse();
    }

    @Test
    @DisplayName("ADMIN dan pengunggahnya melewati pembatasan")
    void adminAndUploaderAlwaysAllowed() {
        Dataset d = datasetWith(new AccessRule(AccessRuleType.JOB_LEVEL, "Direktur"));

        User admin = userWith("Staff", null, null);
        admin.setRole(Role.ADMIN);
        assertThat(guard.canView(admin, d)).isTrue();

        User uploader = userWith("Staff", null, null);
        d.setUploadedBy(uploader);
        assertThat(guard.canView(uploader, d)).isTrue();
    }

    @Test
    @DisplayName("nilai aturan yang rusak tidak menggagalkan seluruh pemeriksaan")
    void ignoresMalformedRuleValue() {
        // Satu baris aturan yang isinya bukan UUID tidak boleh melempar dan
        // menjatuhkan permintaan. Ia sekadar tidak pernah cocok, dan aturan lain
        // di dataset yang sama tetap dinilai.
        Dataset d = datasetWith(
                new AccessRule(AccessRuleType.POSITION, "bukan-uuid"),
                new AccessRule(AccessRuleType.JOB_LEVEL, "Manager"));

        assertThat(guard.canView(userWith("Manager", null, null), d)).isTrue();
        assertThat(guard.canView(userWith("Staff", null, null), d)).isFalse();
    }

    @Test
    @DisplayName("penolakan menyebut jenis pembatasan, tanpa membocorkan UUID")
    void denialExplainsWithoutLeakingUuid() {
        Dataset d = datasetWith(new AccessRule(AccessRuleType.POSITION, DATA_MANAGER_POSITION.toString()));

        assertThatThrownBy(() -> guard.assertCanView(userWith("Staff", null, null), d))
                .isInstanceOf(AccessNotAllowedException.class)
                .hasMessageContaining("Laporan Rahasia")
                .hasMessageContaining("posisi tertentu")
                // UUID tidak berarti apa-apa bagi pembacanya, dan menyebutnya
                // hanya membocorkan pengenal internal HRIS tanpa menolong siapa pun.
                .hasMessageNotContaining(DATA_MANAGER_POSITION.toString());
    }
}
