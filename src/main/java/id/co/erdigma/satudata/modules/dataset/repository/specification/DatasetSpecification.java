package id.co.erdigma.satudata.modules.dataset.repository.specification;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.entity.Format;
import id.co.erdigma.satudata.modules.dataset.entity.Topic;
import id.co.erdigma.satudata.modules.division.entity.Division;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

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
     * Menyaring menurut tag posisi.
     *
     * Wajib dikerjakan di database, bukan di sisi front-end. Begitu daftarnya
     * berhalaman, menyaring hasil satu halaman menghasilkan layar yang
     * membantah dirinya sendiri: tabel menampilkan tiga baris sementara kakinya
     * menyebut dua puluh, dan halaman berikutnya melompati baris yang cocok.
     */
    public static Specification<Dataset> hasPositionIn(List<String> positions) {
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            return root.join("positions", JoinType.INNER).in(positions);
        };
    }

    /**
     * Menyembunyikan dataset yang tidak boleh dilihat pemanggilnya.
     *
     * Aturannya harus sama persis dengan {@code DatasetAccessGuard}: dataset
     * tanpa tag terbuka untuk semua, selain itu posisi pemanggil harus ada di
     * dalam tag, dan pengunggahnya selalu boleh. Kalau keduanya berbeda,
     * seseorang bisa melihat sebuah dataset di daftar lalu ditolak saat
     * membukanya — atau lebih buruk, sebaliknya.
     *
     * @param accessPosition posisi pemanggil; null berarti hanya dataset tanpa
     *                       tag yang terlihat
     * @param userId         pengunggah selalu melihat miliknya sendiri
     */
    public static Specification<Dataset> visibleTo(String accessPosition, UUID userId) {
        return (root, query, cb) -> {
            Expression<java.util.Collection<String>> tags = root.get("positions");
            Predicate terbuka = cb.isEmpty(tags);

            Predicate milikSendiri = (userId == null)
                    ? cb.disjunction()
                    : cb.equal(root.get("uploadedBy").get("id"), userId);

            if (accessPosition == null || accessPosition.isBlank()) {
                return cb.or(terbuka, milikSendiri);
            }
            return cb.or(terbuka, milikSendiri, cb.isMember(accessPosition, tags));
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
