package id.co.erdigma.satudata.modules.dataset.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import id.co.erdigma.satudata.modules.dataset.entity.DatasetCollection;

@Repository
public interface CollectionRepository extends JpaRepository<DatasetCollection, UUID> {

    List<DatasetCollection> findAllByDeletedAtIsNullOrderByNameAsc();

    Optional<DatasetCollection> findBySlugAndDeletedAtIsNull(String slug);

    long countByDeletedAtIsNull();
}
