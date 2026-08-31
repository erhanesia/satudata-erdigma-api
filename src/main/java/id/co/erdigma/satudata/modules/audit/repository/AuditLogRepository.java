package id.co.erdigma.satudata.modules.audit.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import id.co.erdigma.satudata.modules.audit.entity.AuditLog;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByRecordedAtDesc(Pageable pageable);

    Page<AuditLog> findAllByObjectSlugOrderByRecordedAtDesc(String objectSlug, Pageable pageable);
}
