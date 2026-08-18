package id.co.erdigma.satudata.modules.dataset.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import id.co.erdigma.satudata.modules.dataset.entity.Format;

@Repository
public interface FormatRepository extends JpaRepository<Format, UUID>, JpaSpecificationExecutor<Format> {

    List<Format> findAllByDeletedAtIsNullOrderBySortOrderAsc();

    long countByDeletedAtIsNull();
}
