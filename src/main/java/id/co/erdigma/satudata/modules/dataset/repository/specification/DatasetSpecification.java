package id.co.erdigma.satudata.modules.dataset.repository.specification;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.entity.Format;
import id.co.erdigma.satudata.modules.dataset.entity.Topic;
import id.co.erdigma.satudata.modules.division.entity.Division;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;

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
            return cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("notes")), like));
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
