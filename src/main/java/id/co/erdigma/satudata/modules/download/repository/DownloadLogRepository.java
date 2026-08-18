package id.co.erdigma.satudata.modules.download.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import id.co.erdigma.satudata.modules.download.entity.DownloadLog;

@Repository
public interface DownloadLogRepository extends JpaRepository<DownloadLog, Long> {

    Page<DownloadLog> findAllByOrderByDownloadedAtDesc(Pageable pageable);

    Page<DownloadLog> findAllByDatasetIdOrderByDownloadedAtDesc(UUID datasetId, Pageable pageable);

    Page<DownloadLog> findAllByCognitoIdOrderByDownloadedAtDesc(String cognitoId, Pageable pageable);
}
