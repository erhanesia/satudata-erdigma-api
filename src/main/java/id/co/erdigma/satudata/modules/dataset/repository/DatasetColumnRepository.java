package id.co.erdigma.satudata.modules.dataset.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import id.co.erdigma.satudata.modules.dataset.entity.DatasetColumn;

@Repository
public interface DatasetColumnRepository extends JpaRepository<DatasetColumn, UUID> {

    List<DatasetColumn> findAllByDatasetIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID datasetId);

    List<DatasetColumn> findAllByResourceIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID resourceId);

    long countByResourceIdAndDeletedAtIsNull(UUID resourceId);
}
