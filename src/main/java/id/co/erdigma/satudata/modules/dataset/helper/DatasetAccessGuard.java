package id.co.erdigma.satudata.modules.dataset.helper;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.AccessRuleType;
import id.co.erdigma.satudata.enums.Role;
import id.co.erdigma.satudata.exception.AccessNotAllowedException;
import id.co.erdigma.satudata.modules.dataset.entity.AccessRule;
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
 *   <li>Dataset TANPA aturan terbuka untuk seluruh karyawan. Portal ini memang
 *       katalog data bersama; membatasi adalah pengecualian yang harus
 *       dinyatakan, bukan keadaan bawaan.</li>
 *   <li>Peran ADMIN melewati pembatasan. Merekalah yang mengelola katalognya;
 *       admin yang tidak bisa membuka apa yang ia atur tidak bisa memeriksa
 *       pekerjaannya sendiri.</li>
 *   <li>Pengunggahnya selalu boleh. Mengunci penerbit dari datanya sendiri
 *       hanya karena ia memberi aturan untuk orang lain adalah kejutan yang
 *       tidak ada gunanya.</li>
 *   <li>Selain itu: SALAH SATU aturan harus cocok dengan pemanggilnya.</li>
 * </ol>
 *
 * <h2>Ketiga sumbunya sejajar</h2>
 *
 * Aturan EMPLOYEE tidak lebih kuat daripada JOB_LEVEL, hanya lebih sempit.
 * Dataset dengan aturan {@code JOB_LEVEL=MANAGER} dan {@code EMPLOYEE=<Budi>}
 * terlihat oleh seluruh Manager DAN oleh Budi, bukan oleh Manager yang kebetulan
 * bernama Budi.
 *
 * Sebelum changeset 47 hanya ada satu sumbu, yaitu sembilan label karangan di
 * {@code users.access_position}. Kolom itu tidak pernah terisi untuk pengguna
 * Cognito, sehingga setiap karyawan sungguhan ditolak dari seluruh dataset
 * bertag — diam-diam, tanpa galat.
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
        List<AccessRule> rules = dataset.getAccessRules();
        if (rules == null || rules.isEmpty()) {
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
        return rules.stream().anyMatch(rule -> matches(user, rule));
    }

    /**
     * Nilai yang null di sisi pengguna TIDAK PERNAH cocok, meski aturannya juga
     * kosong.
     *
     * Ini penting. Pengguna yang belum tersinkron dari HRIS punya
     * {@code hrisPositionId} null; kalau null dianggap cocok dengan null, ia akan
     * melihat dataset yang justru dibatasi. Gagal ke arah menutup, bukan membuka.
     */
    private boolean matches(User user, AccessRule rule) {
        String value = rule.getRuleValue();
        if (value == null || value.isBlank() || rule.getRuleType() == null) {
            return false;
        }
        return switch (rule.getRuleType()) {
            case JOB_LEVEL -> value.equalsIgnoreCase(user.getJobLevel());
            case POSITION -> sameId(user.getHrisPositionId(), value);
            case EMPLOYEE -> sameId(user.getHrisEmployeeId(), value);
        };
    }

    /**
     * Perbandingan lewat UUID, bukan teks. Penulisan UUID bisa berbeda huruf
     * besar-kecilnya tergantung dari mana ia datang, dan perbandingan teks akan
     * meleset karenanya.
     *
     * Nilai yang tidak bisa diurai sebagai UUID dianggap tidak cocok, bukan
     * dilempar: satu baris aturan yang rusak tidak boleh menggagalkan seluruh
     * pemeriksaan akses.
     */
    private boolean sameId(UUID owned, String value) {
        if (owned == null) {
            return false;
        }
        try {
            return owned.equals(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Pesannya sengaja menyebut apa yang dibutuhkan.
     *
     * Menahan keterangan itu tidak membuat siapa pun lebih aman — orangnya sudah
     * masuk sebagai karyawan, dan judul datasetnya sudah ia lihat. Sebaliknya,
     * "403 Forbidden" tanpa penjelasan hanya berakhir jadi tiket ke tim IT yang
     * harus dijawab dengan kalimat yang sama.
     *
     * UUID posisi dan karyawan TIDAK disebut. Bagi pembacanya itu deretan huruf
     * tanpa arti, dan menerjemahkannya jadi nama berarti memanggil HRIS di
     * tengah jalur penolakan. Yang disebut cukup jenis pembatasannya, supaya
     * orangnya tahu harus bertanya apa kepada admin.
     */
    public void assertCanView(User user, Dataset dataset) {
        if (canView(user, dataset)) {
            return;
        }

        String restrictionKinds = dataset.getAccessRules().stream()
                .map(AccessRule::getRuleType)
                .filter(java.util.Objects::nonNull)
                .map(this::sebutan)
                .distinct()
                .collect(Collectors.joining(", "));

        String jobLevel = (user != null && user.getJobLevel() != null)
                ? user.getJobLevel()
                : "belum diatur";

        throw new AccessNotAllowedException(
                "Dataset \"" + dataset.getTitle() + "\" dibatasi berdasarkan " + restrictionKinds
                        + ". Jenjang jabatan Anda saat ini: " + jobLevel
                        + ". Hubungi admin bila seharusnya Anda punya akses.");
    }

    private String sebutan(AccessRuleType type) {
        return switch (type) {
            case JOB_LEVEL -> "jenjang jabatan";
            case POSITION -> "posisi tertentu";
            case EMPLOYEE -> "karyawan tertentu";
        };
    }
}
