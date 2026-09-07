package id.co.erdigma.satudata.modules.dataset.repository.specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.AccessRuleType;
import id.co.erdigma.satudata.modules.dataset.entity.AccessRule;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.entity.Format;
import id.co.erdigma.satudata.modules.dataset.entity.Topic;
import id.co.erdigma.satudata.modules.division.entity.Division;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

public class DatasetSpecification {

    private DatasetSpecification() {
    }

    /** Selalu benar — titik awal perangkaian, menggantikan Specification.where(null). */
    public static Specification<Dataset> alwaysTrue() {
        return (root, query, cb) -> cb.conjunction();
    }

    public static Specification<Dataset> withoutDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Dataset> search(String search) {
        return (root, query, cb) -> {
            String like = "%" + search.trim().toLowerCase() + "%";
            // Slug ikut dicari karena kotak pencarian di panel admin memang
            // menjanjikannya ("Cari judul atau slug"), dan slug adalah satu-
            // satunya penanda yang dipakai orang saat menyalin tautan atau
            // menyebut dataset di percakapan.
            return cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("slug")), like),
                    cb.like(cb.lower(root.get("notes")), like));
        };
    }

    /**
     * Menyaring daftar berdasarkan jenjang jabatan yang boleh melihat.
     *
     * PENYARING TAMPILAN, bukan pembatas akses. Pembatasannya dipasang terpisah
     * lewat {@link #visibleTo(User)} dan tidak bisa dimatikan lewat parameter
     * apa pun.
     *
     * Hanya menyaring aturan bertipe JOB_LEVEL. Kedua sumbu lainnya bernilai
     * UUID, dan menyaring lewat UUID di parameter URL bukan sesuatu yang bisa
     * diketik manusia — pemilihnya di panel admin pun memakai daftar jenjang
     * yang pendek, bukan ratusan posisi.
     *
     * Wajib dikerjakan di database, bukan di sisi front-end. Begitu daftarnya
     * berhalaman, menyaring hasil satu halaman menghasilkan layar yang
     * membantah dirinya sendiri: tabel menampilkan tiga baris sementara kakinya
     * menyebut dua puluh, dan halaman berikutnya melompati baris yang cocok.
     */
    public static Specification<Dataset> hasJobLevelRuleIn(List<String> jobLevels) {
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            Join<Dataset, AccessRule> rule = root.join("accessRules", JoinType.INNER);
            return cb.and(
                    cb.equal(rule.get("ruleType"), AccessRuleType.JOB_LEVEL),
                    rule.get("ruleValue").in(jobLevels));
        };
    }

    /**
     * Menyembunyikan dataset yang tidak boleh dilihat pemanggilnya.
     *
     * Aturannya harus sama persis dengan {@code DatasetAccessGuard}: dataset
     * tanpa aturan terbuka untuk semua, selain itu salah satu aturan harus cocok
     * dengan pemanggilnya, dan pengunggahnya selalu boleh. Kalau keduanya
     * berbeda, seseorang bisa melihat sebuah dataset di daftar lalu ditolak saat
     * membukanya — atau lebih buruk, sebaliknya.
     *
     * <h2>Kenapa EXISTS dan bukan isMember</h2>
     *
     * Sebelum changeset 47 aturannya berupa satu kolom teks, sehingga
     * {@code cb.isMember(posisi, tags)} sudah cukup. Sekarang tiap aturan punya
     * dua kolom — jenis dan nilai — dan keduanya harus cocok berpasangan.
     * {@code isMember} tidak bisa menyatakan itu.
     *
     * Subquery-nya sengaja memakai root sendiri yang dikaitkan lewat id, bukan
     * join langsung dari root luar. Join akan menggandakan baris dataset saat
     * sebuah dataset punya beberapa aturan, dan itu merusak paginasi: halaman
     * berisi sepuluh baris bisa memuat kurang dari sepuluh dataset berbeda.
     *
     * @param user pemanggilnya; null berarti hanya dataset tanpa aturan yang
     *             terlihat
     */
    public static Specification<Dataset> visibleTo(User user) {
        return (root, query, cb) -> {
            Predicate terbuka = cb.isEmpty(root.get("accessRules"));

            if (user == null || query == null) {
                return terbuka;
            }

            // LEFT JOIN eksplisit, bukan root.get("uploadedBy").get("id").
            // Navigasi berantai pada relasi to-one memang dibaca Hibernate
            // langsung dari kolom FK tanpa join, tetapi menuliskannya sebagai
            // join membuat maksudnya tidak bergantung pada perilaku itu.
            Predicate milikSendiri = (user.getId() == null)
                    ? cb.disjunction()
                    : cb.equal(root.join("uploadedBy", JoinType.LEFT).get("id"), user.getId());

            Subquery<Integer> ada = query.subquery(Integer.class);
            Root<Dataset> lain = ada.from(Dataset.class);
            Join<Dataset, AccessRule> aturan = lain.join("accessRules");

            List<Predicate> cocok = new ArrayList<>();
            if (user.getJobLevel() != null && !user.getJobLevel().isBlank()) {
                cocok.add(cb.and(
                        cb.equal(aturan.get("ruleType"), AccessRuleType.JOB_LEVEL),
                        cb.equal(cb.lower(aturan.get("ruleValue")),
                                user.getJobLevel().toLowerCase(Locale.ROOT))));
            }
            if (user.getHrisPositionId() != null) {
                cocok.add(cb.and(
                        cb.equal(aturan.get("ruleType"), AccessRuleType.POSITION),
                        cb.equal(cb.lower(aturan.get("ruleValue")),
                                user.getHrisPositionId().toString().toLowerCase(Locale.ROOT))));
            }
            if (user.getHrisEmployeeId() != null) {
                cocok.add(cb.and(
                        cb.equal(aturan.get("ruleType"), AccessRuleType.EMPLOYEE),
                        cb.equal(cb.lower(aturan.get("ruleValue")),
                                user.getHrisEmployeeId().toString().toLowerCase(Locale.ROOT))));
            }

            // Tidak satu pun pengenal terisi berarti pemanggilnya belum
            // tersinkron dari HRIS. Ia hanya melihat dataset terbuka dan
            // miliknya sendiri — gagal ke arah menutup, bukan membuka.
            if (cocok.isEmpty()) {
                return cb.or(terbuka, milikSendiri);
            }

            ada.select(cb.literal(1)).where(
                    cb.equal(lain.get("id"), root.get("id")),
                    cb.or(cocok.toArray(new Predicate[0])));

            return cb.or(terbuka, milikSendiri, cb.exists(ada));
        };
    }

    public static Specification<Dataset> hasTopicIn(List<String> topics) {
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            Join<Dataset, Topic> join = root.join("topics", JoinType.INNER);
            return join.get("name").in(topics);
        };
    }

    public static Specification<Dataset> hasFormatIn(List<String> formats) {
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            Join<Dataset, Format> join = root.join("formats", JoinType.INNER);
            return join.get("name").in(formats);
        };
    }

    public static Specification<Dataset> hasDivisionIn(List<String> divisionCodes) {
        return (root, query, cb) -> {
            Join<Dataset, Division> join = root.join("division", JoinType.INNER);
            return join.get("code").in(divisionCodes);
        };
    }
}
