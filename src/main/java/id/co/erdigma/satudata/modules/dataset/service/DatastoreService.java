package id.co.erdigma.satudata.modules.dataset.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.exception.BusinessValidationException;
import id.co.erdigma.satudata.exception.ResourceNotFoundException;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetSummaryResponse;
import id.co.erdigma.satudata.modules.dataset.dto.DatastoreResponse;
import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.helper.DatasetAccessGuard;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetColumn;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetResource;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetRow;
import id.co.erdigma.satudata.modules.dataset.mapper.DatasetMapper;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetColumnRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetResourceRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRowRepository;

import lombok.RequiredArgsConstructor;

/**
 * Membaca isi tabel dataset.
 *
 * Sejak changeset 37 tabelnya milik BERKAS, bukan milik dataset. Satu dataset
 * boleh memuat CSV dan Excel sekaligus, dan masing-masing punya baris serta
 * kolomnya sendiri — karena itu setiap pembacaan di sini selalu dimulai dengan
 * menentukan berkas mana yang dimaksud.
 */
@Service
@RequiredArgsConstructor
public class DatastoreService {

    @Autowired
    private DatasetAccessGuard accessGuard;

    private static final int MAX_PAGE_SIZE = 500;

    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private DatasetResourceRepository datasetResourceRepository;
    @Autowired
    private DatasetRowRepository datasetRowRepository;
    @Autowired
    private DatasetColumnRepository datasetColumnRepository;
    @Autowired
    private DatasetMapper datasetMapper;

    @Transactional(readOnly = true)
    public DatastoreResponse search(User user, String slug, UUID resourceId, int page, int size) {
        Dataset dataset = datasetRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Dataset not found: " + slug));
        accessGuard.assertCanView(user, dataset);

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        DatasetResource resource = resolveResource(dataset, resourceId);

        DatastoreResponse response = new DatastoreResponse();
        response.setSlug(dataset.getSlug());
        response.setPage(safePage);
        response.setSize(safeSize);

        // Dataset yang belum punya berkas, atau berkas yang memang bukan tabel
        // seperti PDF, menjawab kosong dan bukan galat. Tidak ada yang salah
        // dengan sebuah PDF; yang salah hanya kalau kekosongan itu disamarkan
        // sebagai tabel tanpa baris.
        if (resource == null) {
            response.setColumns(List.of());
            response.setRows(List.of());
            response.setTotalRows(0);
            response.setTotalPages(0);
            return response;
        }

        Page<DatasetRow> rows = datasetRowRepository.findAllByResourceIdOrderByRowNumberAsc(
                resource.getId(), PageRequest.of(safePage, safeSize));

        response.setResourceId(resource.getId());
        response.setTotalRows(rows.getTotalElements());
        response.setTotalPages(rows.getTotalPages());
        response.setColumns(datasetMapper.toColumnResponseList(
                datasetColumnRepository.findAllByResourceIdAndDeletedAtIsNullOrderBySortOrderAsc(
                        resource.getId())));
        response.setRows(rows.getContent().stream().map(DatasetRow::getData).toList());
        return response;
    }

    @Transactional(readOnly = true)
    public DatasetSummaryResponse summary(User user, String slug, UUID resourceId,
            String groupBy, String metric) {
        Dataset dataset = datasetRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Dataset not found: " + slug));
        accessGuard.assertCanView(user, dataset);

        DatasetResource resource = resolveResource(dataset, resourceId);
        if (resource == null) {
            throw new BusinessValidationException(
                    "Dataset " + slug + " belum memiliki isi tabel untuk diringkas.");
        }

        List<String> known = datasetColumnRepository
                .findAllByResourceIdAndDeletedAtIsNullOrderBySortOrderAsc(resource.getId())
                .stream().map(DatasetColumn::getMachineName).toList();

        if (known.isEmpty()) {
            throw new BusinessValidationException(
                    "Berkas " + resource.getFileName() + " tidak punya isi tabel untuk diringkas.");
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
                ? datasetRowRepository.aggregateCount(resource.getId(), groupBy)
                : datasetRowRepository.aggregateSum(resource.getId(), groupBy, metric);

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
        response.setTotalRows(datasetRowRepository.countByResourceId(resource.getId()));
        response.setGroups(groups);
        return response;
    }

    /**
     * Menentukan berkas mana yang isinya dibaca.
     *
     * Berkas yang diminta WAJIB milik dataset pada URL. Tanpa pemeriksaan itu,
     * siapa pun yang boleh membuka satu dataset bisa membaca isi tabel dataset
     * lain hanya dengan menukar {@code resourceId} — dan tag posisi yang sudah
     * diperiksa di atas jadi tidak ada artinya.
     *
     * Tanpa permintaan tertentu, yang dipakai adalah berkas bertanda sumber
     * tabel; kalau tidak ada, berkas pertama yang punya baris.
     */
    private DatasetResource resolveResource(Dataset dataset, UUID resourceId) {
        List<DatasetResource> files = datasetResourceRepository
                .findAllByDatasetIdAndDeletedAtIsNullOrderByFormatSortOrderAscFileNameAsc(
                        dataset.getId());

        if (resourceId != null) {
            return files.stream()
                    .filter(r -> resourceId.equals(r.getId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Berkas tidak ditemukan pada dataset " + dataset.getSlug()));
        }

        return files.stream()
                .filter(DatasetResource::isTableSource)
                .findFirst()
                .or(() -> files.stream().filter(r -> r.getRowCount() > 0).findFirst())
                .orElse(null);
    }
}
