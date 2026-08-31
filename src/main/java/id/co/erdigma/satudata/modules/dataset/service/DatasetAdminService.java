package id.co.erdigma.satudata.modules.dataset.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.AuditAction;
import id.co.erdigma.satudata.enums.JobPosition;
import id.co.erdigma.satudata.exception.BusinessValidationException;
import id.co.erdigma.satudata.exception.ResourceNotFoundException;
import id.co.erdigma.satudata.modules.audit.service.AuditLogService;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tindakan pengelolaan dataset dari panel admin: mengganti tag posisi dan
 * menghapus.
 *
 * Dipisahkan dari {@link DatasetService}, yang hanya membaca. Menaruh jalur yang
 * bisa menghapus dataset di kelas yang sama dengan jalur yang melayani halaman
 * publik membuat keduanya harus dibaca bersama setiap kali salah satunya
 * disentuh.
 *
 * Keduanya menulis jejak audit. Itu bukan tambahan opsional: perubahan siapa
 * boleh melihat apa, dan penghapusan dataset, justru dua hal yang paling sering
 * ditanyakan belakangan — "sejak kapan begini, dan siapa yang mengubahnya".
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatasetAdminService {

    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private AuditLogService auditLogService;

    /**
     * Mengganti seluruh tag posisi sebuah dataset.
     *
     * Ini mengubah SIAPA YANG BISA MEMBUKA datanya, seketika: daftar kosong
     * membuatnya terbuka untuk seluruh karyawan, daftar berisi menguncinya ke
     * posisi-posisi itu saja. Karena itu nilai sebelum dan sesudahnya ikut
     * ditulis ke jejak audit — pertanyaan "sejak kapan begini" harus punya
     * jawaban.
     */
    @Transactional
    public List<String> updatePositions(User actor, String slug, List<String> requested) {
        Dataset dataset = fetch(slug);

        List<String> before = new ArrayList<>(dataset.getPositions());
        List<String> after = validate(requested);

        dataset.getPositions().clear();
        dataset.getPositions().addAll(after);
        datasetRepository.save(dataset);

        auditLogService.recordDataset(actor, AuditAction.UPDATE, dataset,
                "Akses posisi diubah dari [" + join(before) + "] menjadi ["
                        + join(after) + "].");

        return after;
    }

    /**
     * Menghapus dataset — SOFT delete, lewat {@code @SQLDelete} pada entity-nya.
     *
     * Barisnya tetap ada beserta slug-nya, dan itu disengaja: slug punya UNIQUE
     * constraint yang tidak peduli pada soft delete, sehingga dataset yang sudah
     * "dihapus" tetap memegang alamatnya. Tautan lama karena itu tidak akan
     * tiba-tiba menunjuk ke dataset lain milik orang lain.
     */
    @Transactional
    public void delete(User actor, String slug) {
        Dataset dataset = fetch(slug);

        // Audit ditulis lebih dulu, selagi datanya masih utuh terbaca. Setelah
        // penghapusan, judulnya hanya bisa didapat dengan membaca baris yang
        // sudah ditandai terhapus.
        auditLogService.recordDataset(actor, AuditAction.DELETE, dataset,
                "Dataset dihapus dari katalog.");

        datasetRepository.delete(dataset);
        log.info("Dataset {} dihapus oleh {}", slug, actor != null ? actor.getCognitoId() : "sistem");
    }

    private Dataset fetch(String slug) {
        return datasetRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Dataset not found: " + slug));
    }

    /**
     * Label yang tidak dikenal ditolak, bukan diabaikan diam-diam. Tag hasil
     * salah ketik tidak akan pernah cocok dengan posisi siapa pun, sehingga
     * datasetnya terkunci dari semua orang kecuali ADMIN dan pengunggahnya —
     * tanpa satu pun galat yang menunjukkan sebabnya.
     */
    private List<String> validate(List<String> requested) {
        List<String> result = new ArrayList<>();
        if (requested == null) {
            return result;
        }
        for (String label : requested) {
            if (label == null || label.isBlank()) {
                continue;
            }
            JobPosition position = JobPosition.fromLabel(label);
            if (position == null) {
                throw new BusinessValidationException(
                        "Posisi \"" + label.trim() + "\" tidak dikenal. Lihat GET /api/v1/positions.");
            }
            if (!result.contains(position.getLabel())) {
                result.add(position.getLabel());
            }
        }
        return result;
    }

    private String join(List<String> position) {
        return position.isEmpty() ? "kosong" : String.join(", ", position);
    }
}
