package id.co.erdigma.satudata.modules.stats.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.modules.division.repository.DivisionRepository;
import id.co.erdigma.satudata.modules.dataset.repository.CollectionRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;
import id.co.erdigma.satudata.modules.dataset.repository.FormatRepository;
import id.co.erdigma.satudata.modules.dataset.repository.TopicRepository;
import id.co.erdigma.satudata.modules.stats.dto.StatsResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StatsService {
    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private DivisionRepository divisionRepository;
    @Autowired
    private TopicRepository topicRepository;
    @Autowired
    private FormatRepository formatRepository;
    @Autowired
    private CollectionRepository collectionRepository;

    @Transactional(readOnly = true)
    public StatsResponse getStats() {
        StatsResponse response = new StatsResponse();
        response.setTotalDataset(datasetRepository.countByDeletedAtIsNull());
        response.setTotalDivision(divisionRepository.countByDeletedAtIsNull());
        response.setTotalTopic(topicRepository.countByDeletedAtIsNull());
        response.setTotalFormat(formatRepository.countByDeletedAtIsNull());
        response.setTotalCollection(collectionRepository.countByDeletedAtIsNull());
        response.setTotalDownloads(datasetRepository.sumDownloads());
        response.setTotalApiCalls(datasetRepository.sumApiCalls());
        response.setTotalViews(datasetRepository.sumViews());
        response.setTotalDatasetWithFile(datasetRepository.countWithResource());
        return response;
    }
}
