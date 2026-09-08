package id.co.erdigma.satudata.modules.download.service;

import java.io.InputStream;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.exception.BusinessValidationException;
import id.co.erdigma.satudata.exception.ResourceNotFoundException;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetResource;
import id.co.erdigma.satudata.modules.dataset.helper.DatasetAccessGuard;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetResourceRepository;
import id.co.erdigma.satudata.modules.download.dto.DownloadPayload;
import id.co.erdigma.satudata.modules.download.entity.DownloadLog;
import id.co.erdigma.satudata.modules.download.repository.DownloadLogRepository;
import id.co.erdigma.satudata.service.storage.FileStorage;
import id.co.erdigma.satudata.service.storage.LocalFileStorage;

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
    @Autowired
    private DatasetAccessGuard accessGuard;

    @Transactional
    public DownloadPayload download(User user, String slug, boolean agreement,
            String ipAddress, String userAgent) {
        return download(user, slug, null, agreement, ipAddress, userAgent);
    }

    /**
     * @param resourceId berkas mana yang diminta. Null berarti berkas pertama —
     *                   bentuk lama, dipertahankan supaya pemanggil yang hanya
     *                   ingin "berkas utama" tidak perlu tahu id apa pun.
     *
     * Satu permintaan mengambil SATU berkas. Modal persetujuan boleh memilih
     * beberapa sekaligus, dan front-end memanggil endpoint ini sekali per
     * berkas — bukan mengemasnya jadi satu arsip. Alasannya audit: tiap berkas
     * yang keluar harus punya barisnya sendiri di download_log, lengkap dengan
     * nama dan ukurannya. Satu baris berbunyi "arsip berisi 3 berkas" tidak bisa
     * menjawab pertanyaan "siapa yang mengambil berkas gaji itu".
     */
    @Transactional
    public DownloadPayload download(User user, String slug, UUID resourceId, boolean agreement,
            String ipAddress, String userAgent) {

        Dataset dataset = datasetRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Dataset not found: " + slug));

        // Diperiksa sebelum apa pun yang lain — termasuk sebelum persetujuan.
        // Orang yang memang tidak berhak tidak perlu diminta menyetujui syarat
        // pemakaian lebih dulu untuk kemudian ditolak.
        accessGuard.assertCanView(user, dataset);

        if (!agreement) {
            throw new BusinessValidationException(
                    "Persetujuan penggunaan data wajib diberikan sebelum mengunduh.");
        }

        DatasetResource resource = (resourceId == null)
                ? datasetResourceRepository
                        .findFirstByDatasetIdAndDeletedAtIsNullOrderByCreatedAtAsc(dataset.getId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Dataset " + slug + " belum memiliki berkas untuk diunduh."))
                : datasetResourceRepository.findById(resourceId)
                        .filter(r -> r.getDeletedAt() == null)
                        // Berkas HARUS milik dataset yang disebut di URL.
                        // Tanpa pemeriksaan ini, siapa pun bisa menyebut slug
                        // dataset yang boleh ia buka lalu menempelkan id berkas
                        // milik dataset lain yang tertutup untuknya — dan
                        // penjagaan akses di atas jadi tidak berarti apa-apa.
                        .filter(r -> r.getDataset().getId().equals(dataset.getId()))
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Berkas tidak ditemukan pada dataset " + slug + "."));

        // Dataset contoh punya keterangan berkas tapi tidak punya isinya. Ditolak
        // DI SINI, sebelum jejak audit ditulis dan sebelum penghitung unduhan
        // naik — kalau dibiarkan lewat, penyimpanan akan melempar "berkas tidak
        // ditemukan", pesan yang benar secara teknis tapi tidak menjelaskan
        // apa pun kepada orang yang menekan tombol Unduh.
        if (LocalFileStorage.SEED_PROVIDER.equalsIgnoreCase(resource.getStorageProvider())) {
            throw new BusinessValidationException(
                    "\"" + dataset.getTitle() + "\" adalah dataset contoh. Keterangan berkasnya "
                            + "ada, tetapi isinya memang tidak disertakan dalam data dummy, "
                            + "sehingga belum ada yang bisa diunduh.");
        }

        // Hanya satu backend penyimpanan yang aktif per proses. Kalau default
        // provider pernah diganti (mis. LOCAL -> S3), baris lama tetap menunjuk ke
        // backend lama dan tidak akan ketemu di backend baru — sama seperti berkas
        // yang benar-benar hilang, jadi diperlakukan sebagai 404, bukan 400. Baris
        // peninggalan sebelum kolom ini diisi (null/kosong) dianggap cocok, bukan
        // digagalkan keras.
        String recordedProvider = resource.getStorageProvider();
        if (recordedProvider != null && !recordedProvider.isBlank()
                && !recordedProvider.equals(fileStorage.getProviderName())) {
            throw new ResourceNotFoundException(
                    "Berkas " + resource.getFileName() + " tidak ditemukan di penyimpanan aktif ("
                            + fileStorage.getProviderName() + "); berkas ini tersimpan di penyimpanan "
                            + recordedProvider
                            + ". Hubungi admin untuk memindahkan berkas ke penyimpanan aktif.");
        }

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
