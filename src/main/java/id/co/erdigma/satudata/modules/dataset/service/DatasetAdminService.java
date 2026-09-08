package id.co.erdigma.satudata.modules.dataset.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.AuditAction;
import id.co.erdigma.satudata.exception.ResourceNotFoundException;
import id.co.erdigma.satudata.modules.audit.service.AuditLogService;
import id.co.erdigma.satudata.modules.dataset.dto.AccessRuleDTO;
import id.co.erdigma.satudata.modules.dataset.entity.AccessRule;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.helper.AccessRuleValidator;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetRequestUpdateDTO;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetResponse;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetCollection;
import id.co.erdigma.satudata.modules.dataset.mapper.DatasetMapper;
import id.co.erdigma.satudata.modules.dataset.repository.CollectionRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;
import id.co.erdigma.satudata.modules.dataset.repository.TopicRepository;
import id.co.erdigma.satudata.modules.dataset.entity.Topic;
import id.co.erdigma.satudata.exception.BusinessValidationException;

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
    @Autowired
    private AccessRuleValidator accessRuleValidator;
    @Autowired
    private DatasetMapper datasetMapper;
    @Autowired
    private TopicRepository topicRepository;
    @Autowired
    private CollectionRepository collectionRepository;

    /**
     * Menyunting keterangan dataset yang sudah terbit.
     *
     * <h2>Yang sengaja TIDAK bisa diubah di sini</h2>
     *
     * <b>Slug.</b> Judul boleh dirapikan berkali-kali; alamatnya tidak ikut
     * berubah. Slug dipakai orang membagikan tautan di grup dan catatan rapat,
     * dan mengubahnya mematikan setiap tautan yang sudah beredar. Konsekuensinya
     * slug bisa terlihat sedikit ketinggalan dari judulnya, dan itu pertukaran
     * yang disengaja.
     *
     * <b>Divisi</b> dan <b>berkas</b>. Keduanya keputusan yang jauh lebih besar
     * daripada merapikan keterangan; berkas sudah punya jalurnya sendiri di
     * {@code reimport}.
     *
     * <h2>Null berarti "jangan diubah"</h2>
     *
     * Berlaku untuk seluruh ruas keterangan, sehingga klien yang hanya ingin
     * mengganti judul tidak perlu ikut mengirim sisanya — dan tidak akan
     * menghapus deskripsi yang tidak ia sentuh. String kosong tetap berarti
     * "kosongkan", karena itu maksud yang dinyatakan.
     *
     * {@code accessRules} dikecualikan: DTO-nya mewajibkan ruas itu ada. Ruas
     * keamanan yang lupa dikirim tidak boleh berakibat sama dengan permintaan
     * yang sengaja membuka.
     */
    @Transactional
    public DatasetResponse update(User actor, String slug, DatasetRequestUpdateDTO body) {
        Dataset dataset = fetch(slug);

        List<String> perubahan = new ArrayList<>();

        String judulBaru = body.getTitle().trim();
        if (!judulBaru.equals(dataset.getTitle())) {
            perubahan.add("judul: \"" + dataset.getTitle() + "\" menjadi \"" + judulBaru + "\"");
            dataset.setTitle(judulBaru);
        }

        if (body.getNotes() != null && !body.getNotes().equals(dataset.getNotes())) {
            perubahan.add("deskripsi diperbarui");
            dataset.setNotes(body.getNotes());
        }
        if (body.getDisclaimer() != null && !body.getDisclaimer().equals(dataset.getDisclaimer())) {
            perubahan.add("disclaimer diperbarui");
            dataset.setDisclaimer(body.getDisclaimer());
        }
        if (body.getCoverage() != null && !body.getCoverage().equals(dataset.getCoverage())) {
            perubahan.add("cakupan diperbarui");
            dataset.setCoverage(body.getCoverage());
        }

        if (body.getTopics() != null) {
            List<Topic> topikBaru = resolveTopics(body.getTopics());
            if (!sameTopics(dataset.getTopics(), topikBaru)) {
                perubahan.add("topik menjadi [" + joinTopics(topikBaru) + "]");
                dataset.getTopics().clear();
                dataset.getTopics().addAll(topikBaru);
            }
        }

        if (body.getCollectionSlug() != null) {
            DatasetCollection koleksiBaru = resolveCollection(body.getCollectionSlug());
            String lama = (dataset.getCollection() != null) ? dataset.getCollection().getSlug() : "-";
            String baru = (koleksiBaru != null) ? koleksiBaru.getSlug() : "-";
            if (!lama.equals(baru)) {
                perubahan.add("koleksi: " + lama + " menjadi " + baru);
                dataset.setCollection(koleksiBaru);
            }
        }

        List<AccessRule> aturanLama = new ArrayList<>(dataset.getAccessRules());
        List<AccessRule> aturanBaru = accessRuleValidator.validate(body.getAccessRules());
        if (!aturanLama.equals(aturanBaru)) {
            perubahan.add("aturan akses dari [" + join(aturanLama) + "] menjadi [" + join(aturanBaru) + "]");
            dataset.getAccessRules().clear();
            dataset.getAccessRules().addAll(aturanBaru);
        }

        datasetRepository.save(dataset);

        // Jejaknya menyebut APA yang berubah, bukan sekadar "dataset disunting".
        // Pertanyaan yang datang belakangan selalu berbentuk "sejak kapan begini",
        // dan catatan tanpa isi tidak menjawabnya.
        //
        // Perubahan kosong tetap dicatat, karena penerbit menekan Simpan dan
        // berhak melihat bahwa tindakannya sampai. Yang dicatat apa adanya:
        // tidak ada yang berubah.
        auditLogService.recordDataset(actor, AuditAction.UPDATE, dataset,
                perubahan.isEmpty()
                        ? "Dataset disunting tanpa perubahan isi."
                        : "Dataset disunting: " + String.join("; ", perubahan) + ".");

        log.info("Dataset {} disunting oleh {} ({} perubahan)", slug,
                actor != null ? actor.getCognitoId() : "sistem", perubahan.size());

        return datasetMapper.toResponse(dataset);
    }

    private boolean sameTopics(List<Topic> a, List<Topic> b) {
        if (a.size() != b.size()) {
            return false;
        }
        return a.stream().map(Topic::getId).toList()
                .containsAll(b.stream().map(Topic::getId).toList());
    }

    private String joinTopics(List<Topic> topics) {
        return topics.stream().map(Topic::getName).reduce((x, y) -> x + ", " + y).orElse("");
    }

    private DatasetCollection resolveCollection(String slug) {
        String cleaned = (slug == null || slug.isBlank()) ? null : slug.trim();
        if (cleaned == null) {
            return null;
        }
        return collectionRepository.findBySlugAndDeletedAtIsNull(cleaned)
                .orElseThrow(() -> new BusinessValidationException(
                        "Koleksi \"" + cleaned + "\" tidak ada. Lihat GET /api/v1/collections."));
    }

    private List<Topic> resolveTopics(List<String> names) {
        List<Topic> result = new ArrayList<>();
        if (names == null || names.isEmpty()) {
            return result;
        }
        List<Topic> all = topicRepository.findAllByDeletedAtIsNullOrderBySortOrderAsc();
        for (String n : names) {
            String cleaned = (n == null || n.isBlank()) ? null : n.trim();
            if (cleaned == null) {
                continue;
            }
            all.stream()
                    .filter(t -> t.getName().equalsIgnoreCase(cleaned))
                    .findFirst()
                    .ifPresentOrElse(result::add, () -> {
                        throw new BusinessValidationException(
                                "Topik \"" + cleaned + "\" tidak ada. Lihat GET /api/v1/topics.");
                    });
        }
        return result;
    }

    /**
     * Mengganti seluruh aturan "siapa boleh melihat" sebuah dataset.
     *
     * Ini mengubah SIAPA YANG BISA MEMBUKA datanya, seketika: daftar kosong
     * membuatnya terbuka untuk seluruh karyawan, daftar berisi menguncinya ke
     * aturan-aturan itu saja. Karena itu nilai sebelum dan sesudahnya ikut
     * ditulis ke jejak audit — pertanyaan "sejak kapan begini" harus punya
     * jawaban.
     *
     * Menimpa seluruhnya, bukan menambah. Antarmuka mengirim keadaan akhir yang
     * diinginkan, dan itu membuat penghapusan satu aturan tidak butuh endpoint
     * tersendiri.
     */
    @Transactional
    public List<AccessRuleDTO> updateAccessRules(User actor, String slug, List<AccessRuleDTO> requested) {
        Dataset dataset = fetch(slug);

        List<AccessRule> before = new ArrayList<>(dataset.getAccessRules());
        List<AccessRule> after = accessRuleValidator.validate(requested);

        dataset.getAccessRules().clear();
        dataset.getAccessRules().addAll(after);
        datasetRepository.save(dataset);

        auditLogService.recordDataset(actor, AuditAction.UPDATE, dataset,
                "Aturan akses diubah dari [" + join(before) + "] menjadi ["
                        + join(after) + "].");

        return after.stream()
                .map(r -> new AccessRuleDTO(r.getRuleType(), r.getRuleValue()))
                .toList();
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
    /**
     * Jejak audit ditulis untuk dibaca manusia, bukan diurai mesin.
     *
     * UUID posisi dan karyawan ikut apa adanya. Menerjemahkannya jadi nama
     * berarti memanggil HRIS di tengah transaksi yang sedang menulis, dan
     * kegagalan panggilan itu akan menggagalkan perubahan yang sebenarnya sudah
     * sah. Jenisnya disebutkan supaya pembacanya tahu UUID itu merujuk apa.
     */
    private String join(List<AccessRule> rules) {
        return rules.isEmpty()
                ? "kosong"
                : rules.stream()
                        .map(r -> r.getRuleType() + "=" + r.getRuleValue())
                        .collect(java.util.stream.Collectors.joining(", "));
    }
}
