package id.co.erdigma.satudata.modules.dataset.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.AuditAction;
import id.co.erdigma.satudata.enums.IdPrefix;
import id.co.erdigma.satudata.exception.ResourceNotFoundException;
import id.co.erdigma.satudata.modules.audit.service.AuditLogService;
import id.co.erdigma.satudata.modules.dataset.dto.AccessRuleDTO;
import id.co.erdigma.satudata.modules.dataset.entity.AccessRule;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.helper.AccessRuleValidator;
import id.co.erdigma.satudata.modules.dataset.helper.RichTextSanitizer;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetRequestUpdateDTO;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetResponse;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetCollection;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetResource;
import id.co.erdigma.satudata.modules.dataset.repository.CollectionRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetResourceRepository;
import id.co.erdigma.satudata.modules.dataset.repository.TopicRepository;
import id.co.erdigma.satudata.modules.dataset.service.DatasetFileService.UploadedFile;
import id.co.erdigma.satudata.modules.dataset.entity.Topic;
import id.co.erdigma.satudata.exception.BusinessValidationException;

import jakarta.persistence.EntityManager;

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
    private RichTextSanitizer richTextSanitizer;
    @Autowired
    private TopicRepository topicRepository;
    @Autowired
    private CollectionRepository collectionRepository;
    @Autowired
    private DatasetResourceRepository datasetResourceRepository;
    @Autowired
    private DatasetFileService datasetFileService;
    @Autowired
    private DatasetService datasetService;
    @Autowired
    private EntityManager entityManager;

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
     * <b>Divisi</b> dan <b>pengunggah</b>. Keduanya jejak siapa yang bertanggung
     * jawab atas dataset ini, dicatat sekali saat penerbitan. Memindahkannya
     * adalah keputusan yang jauh lebih besar daripada merapikan keterangan, dan
     * tidak semestinya tersedia di layar yang sama.
     *
     * <h2>Berkas BISA diubah di sini</h2>
     *
     * Dulu tidak, dan alasannya tidak bertahan: layar sunting dibuat semirip
     * mungkin dengan layar terbit, dan layar terbit dimulai dari berkas. Kartu
     * berkas yang ada tetapi tidak bisa disentuh adalah janji yang tidak ditepati.
     *
     * Bentuknya keadaan-akhir: {@code body.files} adalah daftar berkas yang
     * SEHARUSNYA dimiliki dataset ini sesudah penyimpanan. Berkas lama disebut
     * dengan {@code id}-nya, berkas baru tanpa {@code id} dan dipasangkan menurut
     * urutan dengan bagian multipart. Yang tidak disebut dilepas.
     *
     * Menghilangkan {@code body.files} sama sekali berarti berkasnya tidak
     * disentuh. Itu penting: klien yang cuma memperbaiki salah ketik pada judul
     * tidak boleh kehilangan seluruh berkasnya karena lupa menyebutkannya.
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
    public DatasetResponse update(User actor, String slug, DatasetRequestUpdateDTO body,
            List<MultipartFile> files) {
        Dataset dataset = fetch(slug);

        List<String> perubahan = new ArrayList<>();

        String judulBaru = body.getTitle().trim();
        if (!judulBaru.equals(dataset.getTitle())) {
            perubahan.add("judul: \"" + dataset.getTitle() + "\" menjadi \"" + judulBaru + "\"");
            dataset.setTitle(judulBaru);
        }

        /*
          Dibersihkan LEBIH DULU, baru dibandingkan.

          Kalau urutannya terbalik, badan permintaan yang isinya sama persis
          dengan yang tersimpan tetapi membawa satu atribut terlarang akan
          terbaca sebagai perubahan, tercatat di jejak audit sebagai "deskripsi
          diperbarui", lalu menyimpan isi yang sama. Yang dibandingkan harus
          yang benar-benar akan disimpan.
        */
        if (body.getNotes() != null) {
            String deskripsiBaru = richTextSanitizer.sanitize(body.getNotes());
            if (!deskripsiBaru.equals(dataset.getNotes())) {
                perubahan.add("deskripsi diperbarui");
                dataset.setNotes(deskripsiBaru);
            }
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

        if (body.getFiles() != null) {
            perubahan.addAll(applyFiles(dataset, body.getFiles(), files));
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

        /*
          Dibaca ulang lewat jalur yang sama dengan GET /{slug}, bukan dipetakan
          dari entity yang ada di tangan.

          Sejak berkas ikut bisa disunting, memetakan entity-nya tidak lagi
          cukup: berkas TIDAK dipetakan sebagai koleksi pada Dataset -- ia
          dilekatkan belakangan oleh DatasetService lewat query tersendiri --
          sehingga respons hasil pemetaan akan menyebut daftar berkas yang
          sudah tidak berlaku, atau tidak menyebutnya sama sekali. Formulir
          sunting memuat ulang dirinya dari respons ini, jadi daftar yang basi
          langsung terlihat sebagai berkas yang tetap ada setelah dihapus.

          Tanpa menghitung kunjungan: menyunting dataset bukan mengunjunginya.
        */
        entityManager.flush();
        entityManager.clear();
        // Tanpa IP dan user agent: recordView mati, jadi tidak ada baris log
        // yang ditulis dan tidak ada yang bisa memakainya.
        return datasetService.getBySlug(actor, slug, false, null, null);
    }

    /**
     * Menjadikan berkas dataset ini sama dengan daftar yang diminta.
     *
     * <h2>Semua penolakan lebih dulu, baru satu pun perubahan</h2>
     *
     * Pemeriksaan dikerjakan sampai habis sebelum berkas pertama dilepas atau
     * disimpan. Transaksi memang akan membatalkan baris database kalau nanti
     * ada yang gagal, tetapi penyimpanan berkas tidak ikut dibatalkan semudah
     * itu -- dan permintaan yang setengah jalan lalu ditolak adalah keadaan
     * yang paling sulit dijelaskan kepada orang yang menekan Simpan.
     */
    private List<String> applyFiles(Dataset dataset,
            List<DatasetRequestUpdateDTO.FileEdit> wanted, List<MultipartFile> files) {

        List<DatasetResource> live = datasetFileService.listLive(dataset);
        Map<UUID, DatasetResource> byId = live.stream()
                .collect(Collectors.toMap(DatasetResource::getId, Function.identity()));

        Set<UUID> keptIds = new LinkedHashSet<>();
        List<DatasetRequestUpdateDTO.FileEdit> added = new ArrayList<>();

        /*
          Id hasil uraian, sejajar dengan `wanted`, null untuk entri berkas baru.

          Disimpan sekali supaya perulangan kedua di bawah tidak perlu
          menguraikannya lagi. Kunci map TIDAK dipakai untuk ini: DTO-nya
          memakai Lombok @Data, jadi dua entri yang isinya kebetulan sama akan
          dianggap satu.
        */
        List<UUID> parsedIds = new ArrayList<>();

        for (DatasetRequestUpdateDTO.FileEdit entry : wanted) {
            if (trimToNull(entry.getId()) == null) {
                parsedIds.add(null);
                added.add(entry);
                continue;
            }
            // Awalan `dres-` dilepas di sini. Bentuk berawalan itulah yang
            // dikirim API ke klien, jadi itu pula yang kembali ke sini.
            UUID id = IdPrefix.DATASET_RESOURCE.parse(entry.getId());
            parsedIds.add(id);

            if (!byId.containsKey(id)) {
                throw new BusinessValidationException("Berkas " + entry.getId()
                        + " bukan milik dataset ini, atau sudah dihapus lebih dulu. "
                        + "Muat ulang halamannya supaya daftarnya kembali sesuai.");
            }
            if (!keptIds.add(id)) {
                throw new BusinessValidationException("Berkas " + entry.getId()
                        + " disebut lebih dari sekali.");
            }
        }

        List<MultipartFile> content = (files == null) ? List.of()
                : files.stream().filter(f -> f != null && !f.isEmpty()).toList();

        /*
          Jumlah entri baru harus sama persis dengan jumlah bagian multipart,
          karena keduanya dipasangkan menurut urutan.

          Diperiksa di sini, bukan diserahkan seluruhnya ke pemeriksa bersama:
          keadaan "tidak ada entri baru sama sekali padahal berkasnya terkirim"
          akan lolos di sana sebagai unggahan tanpa keterangan, lalu tersimpan
          dengan nama seadanya. Berkas yang muncul tanpa pernah diminta lebih
          membingungkan daripada permintaan yang ditolak.
        */
        if (added.size() != content.size()) {
            throw new BusinessValidationException("Ada " + content.size()
                    + " berkas terkirim, sedangkan yang dinyatakan sebagai berkas baru ada "
                    + added.size() + ". Jumlah keduanya harus sama karena dipasangkan "
                    + "menurut urutan.");
        }

        if (keptIds.isEmpty() && content.isEmpty()) {
            throw new BusinessValidationException("Dataset harus punya minimal satu berkas. "
                    + "Kalau memang ingin menghilangkannya dari katalog, hapus datasetnya.");
        }

        long keptBytes = keptIds.stream().mapToLong(id -> byId.get(id).getSizeBytes()).sum();
        List<UploadedFile> uploads = datasetFileService.validate(added, files,
                dataset.getTitle(), keptIds.size(), keptBytes);

        // Sejak sini barulah ada yang berubah.
        List<String> perubahan = new ArrayList<>();

        for (int i = 0; i < wanted.size(); i++) {
            UUID id = parsedIds.get(i);
            if (id == null) {
                continue;
            }
            DatasetRequestUpdateDTO.FileEdit entry = wanted.get(i);
            DatasetResource resource = byId.get(id);
            String label = trimToNull(entry.getLabel());
            // Nama kosong berarti tidak disebut, bukan berarti dikosongkan.
            // Berkas tanpa nama tidak punya apa pun untuk ditampilkan di tab
            // Data Explorer selain nama berkas mentahnya.
            if (label != null && !label.equals(resource.getLabel())) {
                perubahan.add("nama berkas \"" + resource.getLabel() + "\" menjadi \""
                        + label + "\"");
                resource.setLabel(label);
                datasetResourceRepository.save(resource);
            }
        }

        List<DatasetResource> dropped = live.stream()
                .filter(r -> !keptIds.contains(r.getId()))
                .toList();
        for (DatasetResource resource : dropped) {
            perubahan.add("berkas \"" + resource.getLabel() + "\" dilepas");
            datasetFileService.remove(resource);
        }

        if (!uploads.isEmpty()) {
            perubahan.add(uploads.size() == 1
                    ? "berkas \"" + uploads.get(0).label() + "\" ditambahkan"
                    : uploads.size() + " berkas ditambahkan");
        }

        /*
          Hanya dijalankan kalau susunan berkasnya benar-benar berubah.

          Keduanya menghitung ulang berkas mana yang mewakili dataset beserta
          jumlah baris dan kolomnya, dan yang kedua ikut menyetel waktu
          perubahan terakhir. Menjalankannya pada penyimpanan yang cuma
          mengganti judul akan membuat dataset terlihat baru diperbarui
          datanya, padahal isinya tidak disentuh sama sekali.
        */
        if (!dropped.isEmpty() || !uploads.isEmpty()) {
            datasetFileService.store(dataset, uploads, false);
            datasetFileService.refreshAggregates(dataset);
        }

        return perubahan;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
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
