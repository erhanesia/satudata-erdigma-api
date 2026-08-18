package id.co.erdigma.satudata.modules.dataset.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.exception.ResourceNotFoundException;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetRequestGetDTO;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetResponse;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetResponseLite;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
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

    private Specification<Dataset> buildSpecification(DatasetRequestGetDTO params) {
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
        return Sort.by(Sort.Direction.ASC, "title");
    }

    @Transactional(readOnly = true)
    public Page<DatasetResponseLite> getAll(DatasetRequestGetDTO params) {
        Pageable pageable = PageRequest.of(params.getPage(), params.getSize(), buildSort(params.getSort()));
        return datasetRepository.findAll(buildSpecification(params), pageable)
                .map(datasetMapper::toResponseLite);
    }

    /**
     * Detail satu dataset — sekaligus satu-satunya tempat kunjungan dicatat.
     *
     * Bukan {@code readOnly}: memanggil endpoint ini menaikkan penghitung
     * {@code views}, yang menjadi isi sel "Total Views" di beranda.
     *
     * Yang dihitung adalah pembukaan halaman detail, bukan pengunjung unik —
     * orang yang sama membuka dataset yang sama tiga kali terhitung tiga.
     * Membedakan pengunjung menuntut identitas kunjungan yang disimpan, dan
     * portal ini sengaja tidak memegang sesi sama sekali (STATELESS).
     */
    @Transactional
    public DatasetResponse getBySlug(String slug) {
        Dataset dataset = datasetRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Dataset not found: " + slug));

        // Dihitung sebelum UPDATE dijalankan, karena UPDATE-nya sengaja
        // melangkahi persistence context — nilai di entity tidak ikut berubah.
        // Kunjungan ini ikut dihitung dalam angka yang dikembalikan, supaya
        // yang dilihat pembuka halaman sama dengan yang dilihat orang
        // berikutnya, bukan tertinggal satu.
        long views = dataset.getViews() + 1;
        datasetRepository.incrementViews(dataset.getId());

        DatasetResponse response = datasetMapper.toResponse(dataset);
        response.setViews(views);
        // Berkas tidak ada di entity Dataset, jadi dilekatkan di sini. Daftar
        // kosong menandakan dataset belum punya berkas untuk diunduh.
        response.setResources(datasetMapper.toResourceResponseList(
                datasetResourceRepository.findAllByDatasetIdAndDeletedAtIsNull(dataset.getId())));
        return response;
    }
}
