package id.co.erdigma.satudata.modules.download.service;

import java.io.InputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.exception.BusinessValidationException;
import id.co.erdigma.satudata.exception.ResourceNotFoundException;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetResource;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetResourceRepository;
import id.co.erdigma.satudata.modules.download.dto.DownloadPayload;
import id.co.erdigma.satudata.modules.download.entity.DownloadLog;
import id.co.erdigma.satudata.modules.download.repository.DownloadLogRepository;
import id.co.erdigma.satudata.service.storage.FileStorage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Alur unduh: periksa karyawan → wajibkan persetujuan → catat audit →
 * baru alirkan byte.
 *
 * Urutannya penting. Audit ditulis SEBELUM byte dikirim supaya tidak ada berkas
 * yang keluar tanpa jejak, bahkan kalau pengiriman gagal di tengah jalan.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DownloadService {

    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private DatasetResourceRepository datasetResourceRepository;
    @Autowired
    private DownloadLogRepository downloadLogRepository;
    @Autowired
    private FileStorage fileStorage;

    @Transactional
    public DownloadPayload download(User user, String slug, boolean agreement,
            String ipAddress, String userAgent) {

        Dataset dataset = datasetRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Dataset not found: " + slug));

        if (!agreement) {
            throw new BusinessValidationException(
                    "Persetujuan penggunaan data wajib diberikan sebelum mengunduh.");
        }

        DatasetResource resource = datasetResourceRepository
                .findFirstByDatasetIdAndDeletedAtIsNullOrderByCreatedAtAsc(dataset.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dataset " + slug + " belum memiliki berkas untuk diunduh."));

        DownloadLog logEntry = new DownloadLog();
        logEntry.setCognitoId(user.getCognitoId());
        logEntry.setUserName(user.getName());
        logEntry.setUserEmail(user.getEmail());
        logEntry.setDivisionCode(user.getDivision() != null ? user.getDivision().getCode() : null);
        logEntry.setDatasetId(dataset.getId());
        logEntry.setDatasetSlug(dataset.getSlug());
        logEntry.setResourceId(resource.getId());
        logEntry.setFileName(resource.getFileName());
        logEntry.setSizeBytes(resource.getSizeBytes());
        logEntry.setAgreementAccepted(true);
        logEntry.setIpAddress(ipAddress);
        logEntry.setUserAgent(userAgent);
        downloadLogRepository.save(logEntry);

        dataset.setDownloads(dataset.getDownloads() + 1);
        datasetRepository.save(dataset);

        log.info("Unduhan tercatat: {} oleh {} ({})", resource.getFileName(),
                user.getName(), user.getCognitoId());

        InputStream content = fileStorage.open(resource.getStorageKey());
        return new DownloadPayload(resource, content);
    }
}
