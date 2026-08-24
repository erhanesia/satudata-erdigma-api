package id.co.erdigma.satudata.modules.collection.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.exception.ResourceNotFoundException;
import id.co.erdigma.satudata.modules.collection.dto.CollectionResponse;
import id.co.erdigma.satudata.modules.collection.mapper.CollectionMapper;
import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.helper.DatasetAccessGuard;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetCollection;
import id.co.erdigma.satudata.modules.dataset.mapper.DatasetMapper;
import id.co.erdigma.satudata.modules.dataset.repository.CollectionRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CollectionService {
    @Autowired
    private CollectionRepository collectionRepository;
    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private CollectionMapper collectionMapper;
    @Autowired
    private DatasetMapper datasetMapper;
    @Autowired
    private DatasetAccessGuard accessGuard;

    @Transactional(readOnly = true)
    public List<CollectionResponse> getAll(User user) {
        return collectionRepository.findAllByDeletedAtIsNullOrderByNameAsc().stream()
                .map(collection -> toResponseWithCount(user, collection))
                .toList();
    }

    /**
     * Isi satu koleksi.
     *
     * Daftarnya disaring lewat penjaga akses yang sama dengan halaman Datasets.
     * Tanpa itu koleksi menjadi jalan memutar: seseorang yang tidak boleh
     * melihat sebuah dataset tetap membaca judul, catatan, dan jumlah barisnya
     * dari sini. Pembatasan yang bisa dilangkahi lewat satu tautan lain sama
     * saja dengan tidak ada.
     *
     * datasetCount ikut menghitung yang tersaring saja, supaya angkanya cocok
     * dengan daftar di bawahnya.
     */
    @Transactional(readOnly = true)
    public CollectionResponse getBySlug(User user, String slug) {
        DatasetCollection collection = collectionRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found: " + slug));

        List<Dataset> datasets = datasetRepository
                .findAllByCollectionIdAndDeletedAtIsNullOrderByTitleAsc(collection.getId())
                .stream()
                .filter(dataset -> accessGuard.canView(user, dataset))
                .toList();

        CollectionResponse response = collectionMapper.toResponse(collection);
        response.setDatasetCount(datasets.size());
        response.setDatasets(datasets.stream().map(datasetMapper::toResponseLite).toList());
        return response;
    }

    private CollectionResponse toResponseWithCount(User user, DatasetCollection collection) {
        CollectionResponse response = collectionMapper.toResponse(collection);
        response.setDatasetCount((int) datasetRepository
                .findAllByCollectionIdAndDeletedAtIsNullOrderByTitleAsc(collection.getId())
                .stream()
                .filter(dataset -> accessGuard.canView(user, dataset))
                .count());
        return response;
    }
}
