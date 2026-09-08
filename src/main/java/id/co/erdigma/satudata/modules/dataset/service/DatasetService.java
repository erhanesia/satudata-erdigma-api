package id.co.erdigma.satudata.modules.dataset.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.Role;
import id.co.erdigma.satudata.exception.ResourceNotFoundException;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetRequestGetDTO;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetResponse;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetResponseLite;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetResource;
import id.co.erdigma.satudata.modules.dataset.helper.DatasetAccessGuard;
import id.co.erdigma.satudata.modules.dataset.mapper.DatasetMapper;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetResourceRepository;
import id.co.erdigma.satudata.modules.dataset.repository.specification.DatasetSpecification;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DatasetService {
    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private DatasetResourceRepository datasetResourceRepository;
    @Autowired
    private DatasetMapper datasetMapper;
    @Autowired
    private DatasetAccessGuard accessGuard;

    private Specification<Dataset> buildSpecification(DatasetRequestGetDTO params, User user) {
        Specification<Dataset> spec = DatasetSpecification.alwaysTrue();

        if (!params.isWithDeleted()) {
            spec = spec.and(DatasetSpecification.withoutDeleted());
        }
        if (params.getSearch() != null && !params.getSearch().isBlank()) {
            spec = spec.and(DatasetSpecification.search(params.getSearch()));
        }
        if (params.getTopics() != null && !params.getTopics().isEmpty()) {
            spec = spec.and(DatasetSpecification.hasTopicIn(params.getTopics()));
        }
        if (params.getFormats() != null && !params.getFormats().isEmpty()) {
            spec = spec.and(DatasetSpecification.hasFormatIn(params.getFormats()));
        }
        if (params.getDivisions() != null && !params.getDivisions().isEmpty()) {
            spec = spec.and(DatasetSpecification.hasDivisionIn(params.getDivisions()));
        }
        if (params.getJobLevels() != null && !params.getJobLevels().isEmpty()) {
            spec = spec.and(DatasetSpecification.hasJobLevelRuleIn(params.getJobLevels()));
        }

        // Pembatasan akses dipasang PALING AKHIR dan tidak bisa dimatikan lewat
        // parameter apa pun. Penyaring di atas adalah keinginan pemanggil;
        // yang ini batas haknya.
        if (user == null || user.getRole() != Role.ADMIN) {
            spec = spec.and(DatasetSpecification.visibleTo(user));
        }
        return spec;
    }

    /**
     * Nilai sort mengikuti desain. "relevance" sementara diurutkan menurut judul
     * karena pencarian masih LIKE — peringkat relevansi sesungguhnya menyusul
     * bersama full-text search Postgres.
     */
    private Sort buildSort(String sort) {
        if ("downloads".equals(sort)) {
            return Sort.by(Sort.Direction.DESC, "downloads");
        }
        if ("updated".equals(sort)) {
            return Sort.by(Sort.Direction.DESC, "lastUpdatedAt");
        }
        if ("created".equals(sort)) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        return Sort.by(Sort.Direction.ASC, "title");
    }

    @Transactional(readOnly = true)
    public Page<DatasetResponseLite> getAll(User user, DatasetRequestGetDTO params) {
        Pageable pageable = PageRequest.of(params.getPage(), params.getSize(), buildSort(params.getSort()));
        Page<DatasetResponseLite> page = datasetRepository
                .findAll(buildSpecification(params, user), pageable)
                .map(datasetMapper::toResponseLite);

        attachResources(page.getContent());
        return page;
    }

    /**
     * Melekatkan daftar berkas ke seluruh baris satu halaman dengan SATU query.
     *
     * Berkas tidak ada di entity Dataset — itu keputusan lama yang sengaja,
     * supaya membaca metadata tidak selalu ikut menarik tabel berkas. Harganya:
     * siapa pun yang butuh berkas harus mengambilnya sendiri. Mengambilnya per
     * baris di dalam perulangan akan menghasilkan satu query untuk tiap dataset;
     * pada halaman berisi 50 baris itu 50 perjalanan ke database untuk data yang
     * bisa diambil sekaligus.
     */
    private void attachResources(List<DatasetResponseLite> rows) {
        if (rows.isEmpty()) {
            return;
        }

        List<UUID> ids = rows.stream().map(DatasetResponseLite::getId).toList();
        Map<UUID, List<DatasetResource>> perDataset = new HashMap<>();
        for (DatasetResource resource : datasetResourceRepository
                .findAllByDatasetIdInAndDeletedAtIsNullOrderByFormatSortOrderAscFileNameAsc(ids)) {
            perDataset.computeIfAbsent(resource.getDataset().getId(), k -> new ArrayList<>())
                    .add(resource);
        }

        for (DatasetResponseLite item : rows) {
            item.setResources(datasetMapper.toResourceResponseList(
                    perDataset.getOrDefault(item.getId(), List.of())));
        }
    }

    /**
     * Detail satu dataset — sekaligus satu-satunya tempat kunjungan dicatat.
     *
     * Yang dihitung adalah pembukaan halaman detail, bukan pengunjung unik —
     * orang yang sama membuka dataset yang sama tiga kali terhitung tiga.
     * Membedakan pengunjung menuntut identitas kunjungan yang disimpan, dan
     * portal ini sengaja tidak memegang sesi sama sekali (STATELESS).
     *
     * @param recordView false untuk membaca TANPA menaikkan penghitung. Dipakai
     *                   panel admin: menengok dataset lewat panel pengelolaan
     *                   bukan kunjungan portal, dan kalau ikut dihitung, angka
     *                   "Total kunjungan" naik setiap kali admin membuka
     *                   dasbornya sendiri.
     */
    @Transactional
    public DatasetResponse getBySlug(User user, String slug, boolean recordView) {
        Dataset dataset = datasetRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Dataset not found: " + slug));

        // Diperiksa SEBELUM kunjungan dicatat. Percobaan yang ditolak bukan
        // kunjungan, dan tidak boleh menaikkan penghitung.
        accessGuard.assertCanView(user, dataset);

        // Dihitung sebelum UPDATE dijalankan, karena UPDATE-nya sengaja
        // melangkahi persistence context — nilai di entity tidak ikut berubah.
        // Kunjungan ini ikut dihitung dalam angka yang dikembalikan, supaya
        // yang dilihat pembuka halaman sama dengan yang dilihat orang
        // berikutnya, bukan tertinggal satu.
        long views = dataset.getViews();
        if (recordView) {
            views += 1;
            datasetRepository.incrementViews(dataset.getId());
        }

        DatasetResponse response = datasetMapper.toResponse(dataset);
        response.setViews(views);
        // Berkas tidak ada di entity Dataset, jadi dilekatkan di sini. Daftar
        // kosong menandakan dataset belum punya berkas untuk diunduh.
        response.setResources(datasetMapper.toResourceResponseList(
                datasetResourceRepository
                        .findAllByDatasetIdAndDeletedAtIsNullOrderByFormatSortOrderAscFileNameAsc(
                                dataset.getId())));
        return response;
    }

    /** Bentuk lama, tetap menghitung kunjungan — dipakai halaman detail portal. */
    @Transactional
    public DatasetResponse getBySlug(User user, String slug) {
        return getBySlug(user, slug, true);
    }
}
