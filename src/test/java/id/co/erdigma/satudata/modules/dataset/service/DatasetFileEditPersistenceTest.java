package id.co.erdigma.satudata.modules.dataset.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.enums.IdPrefix;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetRequestUpdateDTO;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetResource;
import id.co.erdigma.satudata.modules.dataset.entity.Format;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetResourceRepository;
import id.co.erdigma.satudata.modules.dataset.repository.FormatRepository;
import id.co.erdigma.satudata.modules.division.repository.DivisionRepository;

/**
 * Menyunting berkas terhadap Hibernate yang SUNGGUHAN, bukan yang di-mock.
 *
 * <h2>Kenapa kelas ini harus ada, padahal sudah ada tes unitnya</h2>
 *
 * {@link DatasetUpdateTest} menguji keputusan-keputusannya dengan repository
 * di-mock, dan seluruhnya lulus sementara menambah maupun melepas berkas di
 * peramban selalu berakhir 500.
 *
 * Sebabnya bukan salah logika. {@code refreshAggregates} menukar seluruh koleksi
 * {@code formats} dengan daftar hasil {@code Stream.toList()}, dan daftar itu
 * tidak bisa diubah. Hibernate memanggil {@code clear()} padanya saat menyimpan,
 * dan hasilnya {@code UnsupportedOperationException}.
 *
 * Yang menentukan: <b>galatnya baru muncul saat flush</b>, jauh setelah baris
 * yang menyebabkannya selesai berjalan. Repository yang di-mock tidak pernah
 * melakukan flush, jadi tidak ada satu pun tes unit yang bisa melihatnya --
 * berapa pun banyaknya.
 *
 * Karena itu kelas ini menempel pada satu hal saja: apakah penyuntingan berkas
 * benar-benar bisa DISIMPAN. Bukan menguji ulang keputusan yang sudah dijaga di
 * tempat lain.
 *
 * <h2>Kenapa aman dijalankan</h2>
 *
 * Seluruh kelas berada di dalam transaksi yang selalu dibatalkan, jadi data
 * uji tidak pernah tertinggal. Penyimpanan berkas dipin LOCAL sehingga tidak
 * menyentuh AWS, dan penghapusan di penyimpanan memang sengaja ditunda sampai
 * commit -- yang tidak pernah terjadi di sini.
 */
@SpringBootTest(properties = "satudata.storage.provider=LOCAL")
@Transactional
class DatasetFileEditPersistenceTest {

    @Autowired
    private DatasetAdminService datasetAdminService;
    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private DatasetResourceRepository datasetResourceRepository;
    @Autowired
    private FormatRepository formatRepository;
    @Autowired
    private DivisionRepository divisionRepository;

    private Dataset dataset;
    private DatasetResource csv;
    private DatasetResource pdf;

    @BeforeEach
    void seed() {
        dataset = new Dataset();
        dataset.setSlug("uji-sunting-berkas-" + UUID.randomUUID());
        dataset.setTitle("Uji Sunting Berkas");
        dataset.setDivision(divisionRepository.findAll().get(0));
        datasetRepository.save(dataset);

        csv = resource("Rekap", "CSV", dataset.getSlug() + ".csv");
        pdf = resource("Lampiran", "PDF", dataset.getSlug() + ".pdf");

        // Disetel seperti yang dilakukan penerbitan, supaya keadaan awalnya
        // menyerupai dataset sungguhan dan penyusutannya bisa terlihat.
        dataset.getFormats().addAll(List.of(csv.getFormat(), pdf.getFormat()));
        datasetRepository.save(dataset);
    }

    private DatasetResource resource(String label, String formatName, String fileName) {
        Format format = formatRepository.findAllByDeletedAtIsNullOrderBySortOrderAsc().stream()
                .filter(f -> formatName.equals(f.getName()))
                .findFirst()
                .orElseThrow();

        DatasetResource r = new DatasetResource();
        r.setDataset(dataset);
        r.setFormat(format);
        r.setLabel(label);
        r.setFileName(fileName);
        r.setContentType("application/octet-stream");
        r.setStorageProvider("LOCAL");
        // Tidak pernah benar-benar dibaca: melepas berkas hanya menandai
        // barisnya, dan penghapusan di penyimpanan ditunda sampai commit.
        r.setStorageKey("dataset/" + dataset.getSlug() + "/" + fileName);
        r.setSizeBytes(1024);
        return datasetResourceRepository.save(r);
    }

    private DatasetRequestUpdateDTO body() {
        DatasetRequestUpdateDTO b = new DatasetRequestUpdateDTO();
        b.setTitle(dataset.getTitle());
        b.setAccessRules(List.of());
        return b;
    }

    private DatasetRequestUpdateDTO.FileEdit keep(DatasetResource resource) {
        DatasetRequestUpdateDTO.FileEdit f = new DatasetRequestUpdateDTO.FileEdit();
        f.setId(IdPrefix.DATASET_RESOURCE.format(resource.getId()));
        f.setLabel(resource.getLabel());
        return f;
    }

    private List<DatasetResource> live() {
        return datasetResourceRepository
                .findAllByDatasetIdAndDeletedAtIsNullOrderByFormatSortOrderAscFileNameAsc(
                        dataset.getId());
    }

    @Test
    @DisplayName("melepas satu berkas benar-benar tersimpan")
    void droppingAFilePersists() {
        DatasetRequestUpdateDTO b = body();
        b.setFiles(List.of(keep(csv)));

        datasetAdminService.update(null, dataset.getSlug(), b, null);

        assertThat(live()).extracting(DatasetResource::getLabel).containsExactly("Rekap");
    }

    @Test
    @DisplayName("menambah berkas baru benar-benar tersimpan")
    void addingAFilePersists() {
        DatasetRequestUpdateDTO.FileEdit baru = new DatasetRequestUpdateDTO.FileEdit();
        baru.setLabel("Tambahan");
        baru.setFormat("CSV");

        DatasetRequestUpdateDTO b = body();
        b.setFiles(List.of(keep(csv), keep(pdf), baru));

        datasetAdminService.update(null, dataset.getSlug(), b,
                List.of(new MockMultipartFile("files", "tambahan.csv", "text/csv",
                        "kolom_a,kolom_b\n1,2\n".getBytes())));

        assertThat(live()).extracting(DatasetResource::getLabel)
                .containsExactlyInAnyOrder("Rekap", "Lampiran", "Tambahan");
    }

    @Test
    @DisplayName("lencana jenis berkas ikut menyusut saat berkasnya dilepas")
    void formatBadgesFollowTheFiles() {
        /*
         * Bukan sekadar kerapian. Lencana jenis dipakai penyaring di katalog,
         * jadi dataset yang PDF-nya sudah dilepas tetap muncul saat orang
         * menyaring "PDF", lalu membuka dataset yang tidak punya satu pun
         * berkas seperti itu.
         *
         * Sekaligus mengunci pembedaan menurut ID: `format` dipetakan LAZY, dan
         * distinct() biasa akan menciutkan seluruh proxy jadi satu karena
         * equals bawaan Lombok membaca field yang pada proxy masih null.
         */
        assertThat(dataset.getFormats()).extracting(Format::getName)
                .containsExactlyInAnyOrder("CSV", "PDF");

        DatasetRequestUpdateDTO lepas = body();
        lepas.setFiles(List.of(keep(csv)));
        datasetAdminService.update(null, dataset.getSlug(), lepas, null);

        assertThat(datasetRepository.findBySlugAndDeletedAtIsNull(dataset.getSlug()).orElseThrow()
                .getFormats()).extracting(Format::getName).containsExactly("CSV");
    }

    @Test
    @DisplayName("mengganti nama berkas saja tetap tersimpan")
    void renamingPersists() {
        // Jalur yang dulu satu-satunya yang bekerja, dan justru karena itu ia
        // dikunci: ia tidak melewati refreshAggregates sama sekali, jadi
        // kelulusannya tidak pernah membuktikan jalur lainnya sehat.
        DatasetRequestUpdateDTO.FileEdit ganti = keep(csv);
        ganti.setLabel("Rekap Penjualan Ritel");

        DatasetRequestUpdateDTO b = body();
        b.setFiles(List.of(ganti, keep(pdf)));

        datasetAdminService.update(null, dataset.getSlug(), b, null);

        assertThat(live()).extracting(DatasetResource::getLabel)
                .containsExactlyInAnyOrder("Rekap Penjualan Ritel", "Lampiran");
    }
}
