package id.co.erdigma.satudata.modules.audit.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.AuditAction;
import id.co.erdigma.satudata.modules.audit.dto.AuditLogResponse;
import id.co.erdigma.satudata.modules.audit.entity.AuditLog;
import id.co.erdigma.satudata.modules.audit.mapper.AuditLogMapper;
import id.co.erdigma.satudata.modules.audit.repository.AuditLogRepository;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;

import lombok.RequiredArgsConstructor;

/**
 * Menulis dan membaca jejak audit.
 *
 * {@link #recordDataset} sengaja ikut transaksi pemanggilnya, bukan membuka
 * transaksi sendiri. Kalau penerbitan dataset gagal di tengah jalan, catatan
 * "dataset dibuat" harus ikut batal — audit yang mencatat kejadian yang tidak
 * pernah terjadi lebih buruk daripada tidak ada audit sama sekali.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private AuditLogMapper auditLogMapper;

    /** Batas atas ukuran halaman, supaya satu permintaan tidak menarik seluruh tabel. */
    private static final int MAX_SIZE = 200;

    /**
     * Mencatat tindakan terhadap objek selain dataset.
     *
     * Dipakai antara lain saat log unduhan diekspor: berkas itu memuat nama,
     * email, dan alamat IP karyawan, dan siapa yang mengambilnya keluar dari
     * sistem adalah persis pertanyaan yang harus bisa dijawab belakangan.
     */
    @Transactional
    public void record(User actor, AuditAction action, String objectType, String objectSlug,
            String objectLabel, String detail) {
        AuditLog entry = new AuditLog();
        fillActor(entry, actor);
        entry.setAction(action);
        entry.setObjectType(objectType);
        entry.setObjectSlug(objectSlug);
        entry.setObjectLabel(objectLabel);
        entry.setDetail(detail);
        auditLogRepository.save(entry);
    }

    @Transactional
    public void recordDataset(User actor, AuditAction action, Dataset dataset, String detail) {
        AuditLog entry = new AuditLog();
        fillActor(entry, actor);
        entry.setAction(action);
        entry.setObjectType("dataset");
        entry.setObjectSlug(dataset.getSlug());
        // Judul disalin, bukan dirujuk — supaya baris ini tetap menunjukkan
        // nama yang berlaku SAAT tindakan terjadi, bukan nama terbarunya.
        entry.setObjectLabel(dataset.getTitle());
        entry.setDetail(detail);
        auditLogRepository.save(entry);
    }

    private void fillActor(AuditLog entry, User actor) {
        if (actor == null) {
            return;
        }
        entry.setActorCognitoId(actor.getCognitoId());
        entry.setActorName(actor.getName());
        entry.setActorDivisionCode(
                actor.getDivision() != null ? actor.getDivision().getCode() : null);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAll(int page, int size, String slug) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE));
        Page<AuditLog> result = (slug == null || slug.isBlank())
                ? auditLogRepository.findAllByOrderByRecordedAtDesc(pageable)
                : auditLogRepository.findAllByObjectSlugOrderByRecordedAtDesc(slug.trim(), pageable);
        return result.map(auditLogMapper::toResponse);
    }
}
