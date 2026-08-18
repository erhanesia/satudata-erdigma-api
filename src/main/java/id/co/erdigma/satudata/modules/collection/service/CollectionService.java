package id.co.erdigma.satudata.modules.collection.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.exception.ResourceNotFoundException;
import id.co.erdigma.satudata.modules.collection.dto.CollectionResponse;
import id.co.erdigma.satudata.modules.collection.mapper.CollectionMapper;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
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

    @Transactional(readOnly = true)
    public List<CollectionResponse> getAll() {
        return collectionRepository.findAllByDeletedAtIsNullOrderByNameAsc().stream()
                .map(this::toResponseWithCount)
                .toList();
    }

    @Transactional(readOnly = true)
    public CollectionResponse getBySlug(String slug) {
        DatasetCollection collection = collectionRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found: " + slug));

        List<Dataset> datasets = datasetRepository
                .findAllByCollectionIdAndDeletedAtIsNullOrderByTitleAsc(collection.getId());

        CollectionResponse response = collectionMapper.toResponse(collection);
        response.setDatasetCount(datasets.size());
        response.setDatasets(datasets.stream().map(datasetMapper::toResponseLite).toList());
        return response;
    }

    private CollectionResponse toResponseWithCount(DatasetCollection collection) {
        CollectionResponse response = collectionMapper.toResponse(collection);
        response.setDatasetCount(datasetRepository
                .findAllByCollectionIdAndDeletedAtIsNullOrderByTitleAsc(collection.getId()).size());
        return response;
    }
}
