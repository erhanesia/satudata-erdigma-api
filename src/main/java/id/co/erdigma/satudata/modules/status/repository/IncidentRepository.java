package id.co.erdigma.satudata.modules.status.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import id.co.erdigma.satudata.modules.status.entity.Incident;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    List<Incident> findAllByDeletedAtIsNullOrderByOccurredAtDesc();
}
