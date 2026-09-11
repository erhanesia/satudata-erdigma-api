package id.co.erdigma.satudata.modules.dataset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.enums.IdPrefix;
import id.co.erdigma.satudata.exception.BusinessValidationException;
import id.co.erdigma.satudata.modules.dataset.dto.DatasetRequestUpdateDTO;
import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.entity.DatasetResource;
import id.co.erdigma.satudata.modules.dataset.entity.Format;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetResourceRepository;
import id.co.erdigma.satudata.modules.dataset.repository.FormatRepository;
import id.co.erdigma.satudata.modules.division.repository.DivisionRepository;

/**
 * Batas total dataset diuji terhadap ukuran SESUNGGUHNYA, bukan ukuran terkirim.
 *
 * <h2>Keadaan yang dijaga kelas ini</h2>
 *
 * Sejak peramban boleh mengecilkan CSV sebelum mengirimnya, ada dua angka
 * berbeda untuk satu berkas yang sama: byte yang lewat kabel, dan byte yang
 * sungguh diterima orang saat mengunduh. {@code validate} berjalan sebelum
 * berkasnya sampai, jadi yang bisa ia jumlahkan hanya yang pertama, sementara
 * ukuran berkas lama yang dipertahankan datang dari kolom {@code sizeBytes},
 * yang berisi yang kedua.
 *
 * Penjumlahan yang mencampur keduanya meloloskan dataset melewati batasnya
 * sendiri, dan akibatnya baru terasa pada penyuntingan BERIKUTNYA: ukuran
 * berkas lamanya sudah di atas batas, jadi dataset itu tidak bisa lagi
 * ditambahi apa pun, tanpa ada yang bisa menjelaskan kenapa.
 *
 * <h2>Kenapa tes ini butuh Spring yang sungguhan</h2>
 *
 * Yang diuji bukan keputusannya melainkan ANGKANYA, dan angka itu baru ada
 * setelah gzip-nya dibuka, berkasnya disimpan, dan barisnya ter-flush ke
 * database. Tidak satu pun dari ketiganya terjadi pada repository yang
 * di-mock.
 *
 * <h2>Kenapa aman dijalankan</h2>
 *
 * Seluruh kelas berada di dalam transaksi yang selalu dibatalkan, dan
 * penyimpanan berkas dipin LOCAL sehingga tidak menyentuh AWS. Ukuran berkas
 * lama yang besar itu cuma angka di sebuah kolom: tidak ada 60 MB yang
 * benar-benar ditulis ke mana pun.
 */
@SpringBootTest(properties = "satudata.storage.provider=LOCAL")
@Transactional
class DatasetTotalSizePersistenceTest {

    /**
     * Sengaja dibuat sangat berulang.
     *
     * Yang diuji justru selisih antara kedua angkanya, jadi isinya harus
     * menyusut habis-habisan saat di-gzip: sekitar 40 KB ini terkirim sebagai
     * ratusan byte saja. CSV sungguhan berisi surel dan kode divisi yang
     * berulang ribuan kali berperilaku sama, hanya tidak seekstrem ini.
     */
    private static final int PANJANG_NILAI = 500;
    private static final int JUMLAH_BARIS = 80;

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
    private DatasetResource lama;

    @BeforeEach
    void seed() {
        dataset = new Dataset();
        dataset.setSlug("uji-batas-total-" + UUID.randomUUID());
        dataset.setTitle("Uji Batas Total");
        dataset.setDivision(divisionRepository.findAll().get(0));
        datasetRepository.save(dataset);
    }

    private DatasetResource berkasLama(long sizeBytes) {
        Format format = formatRepository.findAllByDeletedAtIsNullOrderBySortOrderAsc().stream()
                .filter(f -> "CSV".equals(f.getName()))
                .findFirst()
                .orElseThrow();

        DatasetResource r = new DatasetResource();
        r.setDataset(dataset);
        r.setFormat(format);
        r.setLabel("Rekap Lama");
        r.setFileName(dataset.getSlug() + ".csv");
        r.setContentType("text/csv");
        r.setStorageProvider("LOCAL");
        r.setStorageKey("dataset/" + dataset.getSlug() + "/" + dataset.getSlug() + ".csv");
        r.setSizeBytes(sizeBytes);
        r = datasetResourceRepository.save(r);

        dataset.getFormats().add(format);
        datasetRepository.save(dataset);
        return r;
    }

    private static byte[] csvAsli() {
        StringBuilder sb = new StringBuilder();
        sb.append("kolom_a,kolom_b\n");
        for (int i = 1; i <= JUMLAH_BARIS; i++) {
            sb.append(i).append(',').append("x".repeat(PANJANG_NILAI)).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Meniru persis apa yang dikirim peramban: byte gzip, dengan nama berkas
     * yang tetap berakhiran .csv karena dari situlah jenisnya dibaca.
     */
    private static MockMultipartFile terkirimTerkompresi() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(buffer)) {
            gz.write(csvAsli());
        }
        return new MockMultipartFile("files", "tambahan.csv", "text/csv", buffer.toByteArray());
    }

    private DatasetRequestUpdateDTO permintaan(MockMultipartFile unggahan) {
        DatasetRequestUpdateDTO.FileEdit pertahankan = new DatasetRequestUpdateDTO.FileEdit();
        pertahankan.setId(IdPrefix.DATASET_RESOURCE.format(lama.getId()));
        pertahankan.setLabel(lama.getLabel());

        DatasetRequestUpdateDTO.FileEdit baru = new DatasetRequestUpdateDTO.FileEdit();
        baru.setLabel("Tambahan");
        baru.setFormat("CSV");

        DatasetRequestUpdateDTO b = new DatasetRequestUpdateDTO();
        b.setTitle(dataset.getTitle());
        b.setAccessRules(List.of());
        b.setFiles(List.of(pertahankan, baru));
        return b;
    }

    @Test
    @DisplayName("unggahan yang terkirim kecil tetap ditolak bila isinya melewati batas total")
    void compressedUploadCannotSlipPastTheTotalLimit() throws IOException {
        MockMultipartFile unggahan = terkirimTerkompresi();

        /*
          Sisa jatahnya dibuat lebih besar daripada byte TERKIRIM, tetapi lebih
          kecil daripada isinya. Di situlah letak cacatnya: pemeriksaan sebelum
          unggahan melihat angka pertama dan meluluskannya, padahal yang
          tersimpan angka kedua.
        */
        long isi = csvAsli().length;
        assertThat(unggahan.getSize())
                .as("prasyarat: byte terkirim harus jauh lebih kecil daripada isinya")
                .isLessThan(isi / 10);

        long sisaJatah = (isi + unggahan.getSize()) / 2;
        lama = berkasLama(DatasetFileService.MAX_TOTAL_BYTES - sisaJatah);

        assertThatThrownBy(() -> datasetAdminService.update(null, dataset.getSlug(),
                permintaan(unggahan), List.of(unggahan)))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("setelah dibuka");
    }

    @Test
    @DisplayName("unggahan yang sama diterima ketika jatahnya memang cukup")
    void theSameUploadIsAcceptedWhenItActuallyFits() throws IOException {
        // Pasangan tes di atas. Tanpa ini, pemeriksaan yang menolak segalanya
        // juga akan lulus, dan penolakan yang salah justru lebih merugikan
        // daripada cacat yang diperbaiki.
        MockMultipartFile unggahan = terkirimTerkompresi();
        lama = berkasLama(1024);

        datasetAdminService.update(null, dataset.getSlug(), permintaan(unggahan),
                List.of(unggahan));

        DatasetResource tersimpan = datasetResourceRepository
                .findAllByDatasetIdAndDeletedAtIsNullOrderByFormatSortOrderAscFileNameAsc(
                        dataset.getId())
                .stream()
                .filter(r -> "Tambahan".equals(r.getLabel()))
                .findFirst()
                .orElseThrow();

        // Sekaligus mengunci asal angkanya: yang dicatat ukuran setelah dibuka,
        // bukan ukuran yang lewat kabel maupun ukurannya di penyimpanan.
        assertThat(tersimpan.getSizeBytes()).isEqualTo(csvAsli().length);
    }
}
