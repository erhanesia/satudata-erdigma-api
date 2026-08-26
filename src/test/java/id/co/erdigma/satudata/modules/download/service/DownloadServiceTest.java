package id.co.erdigma.satudata.modules.download.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.exception.ResourceNotFoundException;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetResource;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetResourceRepository;
import id.co.erdigma.satudata.modules.download.repository.DownloadLogRepository;
import id.co.erdigma.satudata.service.storage.FileStorage;

/**
 * Tes murni (tanpa Spring context/database): dependensi berupa antarmuka
 * repository Spring Data ditiru dengan Mockito, bukan fake tulisan tangan —
 * JpaRepository punya lusinan method, jadi fake manual di sini tidak lazy.
 */
class DownloadServiceTest {

    private final DatasetRepository datasetRepository = mock(DatasetRepository.class);
    private final DatasetResourceRepository datasetResourceRepository = mock(DatasetResourceRepository.class);
    private final DownloadLogRepository downloadLogRepository = mock(DownloadLogRepository.class);
    private final FileStorage fileStorage = mock(FileStorage.class);
    private final DownloadService downloadService = new DownloadService();

    @BeforeEach
    void wireFields() {
        ReflectionTestUtils.setField(downloadService, "datasetRepository", datasetRepository);
        ReflectionTestUtils.setField(downloadService, "datasetResourceRepository", datasetResourceRepository);
        ReflectionTestUtils.setField(downloadService, "downloadLogRepository", downloadLogRepository);
        ReflectionTestUtils.setField(downloadService, "fileStorage", fileStorage);
    }

    private Dataset siapkanDataset(String slug) {
        Dataset dataset = new Dataset();
        dataset.setId(UUID.randomUUID());
        dataset.setSlug(slug);
        dataset.setDownloads(0);
        when(datasetRepository.findBySlugAndDeletedAtIsNull(slug)).thenReturn(Optional.of(dataset));
        return dataset;
    }

    private void siapkanResource(Dataset dataset, DatasetResource resource) {
        when(datasetResourceRepository
                .findFirstByDatasetIdAndDeletedAtIsNullOrderByCreatedAtAsc(dataset.getId()))
                .thenReturn(Optional.of(resource));
    }

    @Test
    @DisplayName("Provider berkas beda dari provider aktif dianggap tidak ditemukan dan tidak tercatat sebagai unduhan")
    void providerBerbedaDitolakSebelumTercatat() {
        Dataset dataset = siapkanDataset("contoh");
        DatasetResource resource = new DatasetResource();
        resource.setFileName("contoh.csv");
        resource.setStorageProvider("LOCAL");
        siapkanResource(dataset, resource);
        when(fileStorage.getProviderName()).thenReturn("S3");

        assertThatThrownBy(() -> downloadService.download(new User(), "contoh", true, "127.0.0.1", "agent"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("LOCAL")
                .hasMessageContaining("S3");

        // Yang gagal dibuka jangan sampai tercatat sudah diunduh.
        verifyNoInteractions(downloadLogRepository);
        verify(datasetRepository, never()).save(any());
        verify(fileStorage, never()).open(any());
    }

    @Test
    @DisplayName("Provider null (baris peninggalan) tidak digagalkan, tetap boleh diunduh")
    void providerNullDianggapCocok() {
        Dataset dataset = siapkanDataset("lawas");
        DatasetResource resource = new DatasetResource();
        resource.setFileName("lawas.csv");
        resource.setStorageProvider(null);
        siapkanResource(dataset, resource);
        when(fileStorage.getProviderName()).thenReturn("S3");
        InputStream isi = new ByteArrayInputStream("a".getBytes());
        when(fileStorage.open(any())).thenReturn(isi);

        assertThat(downloadService.download(new User(), "lawas", true, "127.0.0.1", "agent").getContent())
                .isSameAs(isi);

        verify(downloadLogRepository).save(any());
    }

    @Test
    @DisplayName("Provider kosong/spasi (baris peninggalan) tidak digagalkan, tetap boleh diunduh")
    void providerKosongDianggapCocok() {
        Dataset dataset = siapkanDataset("lawas-spasi");
        DatasetResource resource = new DatasetResource();
        resource.setFileName("lawas-spasi.csv");
        resource.setStorageProvider("   ");
        siapkanResource(dataset, resource);
        when(fileStorage.getProviderName()).thenReturn("S3");
        InputStream isi = new ByteArrayInputStream("a".getBytes());
        when(fileStorage.open(any())).thenReturn(isi);

        assertThat(downloadService.download(new User(), "lawas-spasi", true, "127.0.0.1", "agent").getContent())
                .isSameAs(isi);

        verify(downloadLogRepository).save(any());
    }
}
