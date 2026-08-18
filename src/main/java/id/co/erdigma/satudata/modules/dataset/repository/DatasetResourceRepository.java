package id.co.erdigma.satudata.modules.dataset.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import id.co.erdigma.satudata.modules.dataset.entity.DatasetResource;

@Repository
public interface DatasetResourceRepository
        extends JpaRepository<DatasetResource, UUID>, JpaSpecificationExecutor<DatasetResource> {

    List<DatasetResource> findAllByDatasetIdAndDeletedAtIsNull(UUID datasetId);

    Optional<DatasetResource> findFirstByDatasetIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID datasetId);
}
