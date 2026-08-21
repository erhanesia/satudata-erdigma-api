package id.co.erdigma.satudata.modules.division.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import id.co.erdigma.satudata.modules.division.entity.Division;

@Repository
public interface DivisionRepository extends JpaRepository<Division, UUID>, JpaSpecificationExecutor<Division> {

    List<Division> findAllByDeletedAtIsNullOrderByApiCallsDesc();

    long countByDeletedAtIsNull();

    Optional<Division> findByHrisDepartementIdAndDeletedAtIsNull(UUID hrisDepartementId);
}
