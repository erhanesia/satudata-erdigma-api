package id.co.erdigma.satudata.modules.dataset.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.exception.BusinessValidationException;
import id.co.erdigma.satudata.exception.ResourceNotFoundException;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetSummaryResponse;
import id.co.erdigma.satudata.modules.dataset.dto.DatastoreResponse;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetColumn;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetRow;
import id.co.erdigma.satudata.modules.dataset.mapper.DatasetMapper;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetColumnRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRowRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DatastoreService {

    private static final int MAX_PAGE_SIZE = 500;

    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private DatasetRowRepository datasetRowRepository;
    @Autowired
    private DatasetColumnRepository datasetColumnRepository;
    @Autowired
    private DatasetMapper datasetMapper;

    @Transactional(readOnly = true)
    public DatastoreResponse search(String slug, int page, int size) {
        Dataset dataset = datasetRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Dataset not found: " + slug));

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        Page<DatasetRow> rows = datasetRowRepository.findAllByDatasetIdOrderByRowNumberAsc(
                dataset.getId(), PageRequest.of(safePage, safeSize));

        List<java.util.Map<String, Object>> content = rows.getContent().stream()
                .map(DatasetRow::getData)
                .toList();

        DatastoreResponse response = new DatastoreResponse();
        response.setSlug(dataset.getSlug());
        response.setTotalRows(rows.getTotalElements());
        response.setPage(safePage);
        response.setSize(safeSize);
        response.setTotalPages(rows.getTotalPages());
        response.setColumns(datasetMapper.toColumnResponseList(
                datasetColumnRepository.findAllByDatasetIdAndDeletedAtIsNullOrderBySortOrderAsc(dataset.getId())));
        response.setRows(content);
        return response;
    }

    @Transactional(readOnly = true)
    public DatasetSummaryResponse summary(String slug, String groupBy, String metric) {
        Dataset dataset = datasetRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Dataset not found: " + slug));

        List<String> known = datasetColumnRepository
                .findAllByDatasetIdAndDeletedAtIsNullOrderBySortOrderAsc(dataset.getId())
                .stream().map(DatasetColumn::getMachineName).toList();

        if (known.isEmpty()) {
            throw new BusinessValidationException(
                    "Dataset " + slug + " belum memiliki isi tabel untuk diringkas.");
        }
        if (groupBy == null || !known.contains(groupBy)) {
            throw new BusinessValidationException(
                    "Parameter groupBy harus salah satu dari: " + String.join(", ", known));
        }
        if (metric != null && !known.contains(metric)) {
            throw new BusinessValidationException(
                    "Parameter metric harus salah satu dari: " + String.join(", ", known));
        }

        List<Object[]> raw = metric == null
                ? datasetRowRepository.aggregateCount(dataset.getId(), groupBy)
                : datasetRowRepository.aggregateSum(dataset.getId(), groupBy, metric);

        List<DatasetSummaryResponse.SummaryGroup> groups = raw.stream().map(r -> {
            DatasetSummaryResponse.SummaryGroup g = new DatasetSummaryResponse.SummaryGroup();
            g.setLabel(r[0] == null ? null : r[0].toString());
            g.setCount(((Number) r[1]).longValue());
            g.setSum(r.length > 2 && r[2] != null ? new BigDecimal(r[2].toString()) : null);
            return g;
        }).toList();

        DatasetSummaryResponse response = new DatasetSummaryResponse();
        response.setSlug(dataset.getSlug());
        response.setGroupBy(groupBy);
        response.setMetric(metric);
        response.setTotalRows(datasetRowRepository.countByDatasetId(dataset.getId()));
        response.setGroups(groups);
        return response;
    }
}
