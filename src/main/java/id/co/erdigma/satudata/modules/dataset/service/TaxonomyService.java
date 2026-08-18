package id.co.erdigma.satudata.modules.dataset.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.modules.dataset.dto.FormatResponse;
import id.co.erdigma.satudata.modules.dataset.dto.TopicResponse;
import id.co.erdigma.satudata.modules.dataset.mapper.DatasetMapper;
import id.co.erdigma.satudata.modules.dataset.repository.FormatRepository;
import id.co.erdigma.satudata.modules.dataset.repository.TopicRepository;

import lombok.RequiredArgsConstructor;

/**
 * Daftar topik dan format — pengisi chip filter di halaman Datasets.
 */
@Service
@RequiredArgsConstructor
public class TaxonomyService {
    @Autowired
    private TopicRepository topicRepository;
    @Autowired
    private FormatRepository formatRepository;
    @Autowired
    private DatasetMapper datasetMapper;

    @Transactional(readOnly = true)
    public List<TopicResponse> getAllTopic() {
        return datasetMapper.toTopicResponseList(topicRepository.findAllByDeletedAtIsNullOrderBySortOrderAsc());
    }

    @Transactional(readOnly = true)
    public List<FormatResponse> getAllFormat() {
        return datasetMapper.toFormatResponseList(formatRepository.findAllByDeletedAtIsNullOrderBySortOrderAsc());
    }
}
