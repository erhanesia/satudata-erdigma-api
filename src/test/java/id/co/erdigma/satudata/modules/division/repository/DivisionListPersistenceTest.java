package id.co.erdigma.satudata.modules.division.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.modules.dataset.entity.Dataset;
import id.co.erdigma.satudata.modules.dataset.repository.DatasetRepository;
import id.co.erdigma.satudata.modules.division.dto.DivisionResponse;
import id.co.erdigma.satudata.modules.division.entity.Division;
import id.co.erdigma.satudata.modules.division.service.DivisionService;

/**
 * Aturan tampil daftar divisi, dijalankan terhadap Postgres yang sebenarnya.
 *
 * <h2>Aturannya</h2>
 *
 * Divisi tampil kalau punya karyawan di HRIS, atau punya dataset yang belum
 * dihapus. Divisi tanpa keduanya disembunyikan dari daftar, tetapi barisnya
 * tetap hidup supaya pencocokan saat login masih menemukannya.
 *
 * <h2>Kenapa perlu database sungguhan</h2>
 *
 * Syaratnya duduk di {@code HAVING} atas {@code LEFT JOIN} yang dikelompokkan.
 * Salah menulisnya tidak melempar apa pun: daftar tetap terisi dan berurutan,
 * hanya isinya yang keliru. Menghitung {@code ds.id} alih-alih baris dataset
 * yang sah, atau lupa bahwa dataset terhapus sudah disaring di {@code ON},
 * sama-sama menghasilkan daftar yang terlihat wajar.
 *
 * <h2>Kenapa kehadiran, bukan jumlah</h2>
 *
 * Database pengembang sudah berisi divisi, dan isinya berbeda di tiap mesin.
 * Yang diperiksa hanya divisi yang disisipkan tes ini sendiri, dengan kode acak
 * yang pasti tidak bentrok.
 *
 * <h2>Kenapa aman dijalankan</h2>
 *
 * {@code @Transactional} menggulung balik setiap metode, dan penyimpanan berkas
 * dipin LOCAL sehingga tidak menyentuh AWS.
 */
@SpringBootTest(properties = "satudata.storage.provider=LOCAL")
@Transactional
class DivisionListPersistenceTest {

    @Autowired
    private DivisionService divisionService;
    @Autowired
    private DivisionRepository divisionRepository;
    @Autowired
    private DatasetRepository datasetRepository;

    private Division division(int employeeCount, UUID hrisTeamId) {
        String code = "T" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 10).toUpperCase(Locale.ROOT);

        Division division = new Division();
        division.setCode(code);
        division.setName("Uji Daftar Divisi " + code);
        division.setHrisTeamId(hrisTeamId);
        division.setHrisEmployeeCount(employeeCount);
        return divisionRepository.saveAndFlush(division);
    }

    private Dataset dataset(Division division, boolean deleted) {
        Dataset dataset = new Dataset();
        dataset.setSlug("uji-daftar-divisi-" + UUID.randomUUID());
        dataset.setTitle("Uji Daftar Divisi");
        dataset.setDivision(division);
        if (deleted) {
            dataset.setDeletedAt(LocalDateTime.now());
        }
        return datasetRepository.saveAndFlush(dataset);
    }

    private Set<UUID> listedIds() {
        return divisionService.getAll().stream()
                .map(DivisionResponse::getId)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("divisi tanpa karyawan dan tanpa dataset tidak ditampilkan")
    void divisionWithoutEmployeesOrDatasetsIsHidden() {
        Division empty = division(0, null);

        assertThat(listedIds()).doesNotContain(empty.getId());
    }

    @Test
    @DisplayName("divisi yang punya karyawan tetap tampil walau belum punya dataset")
    void divisionWithEmployeesIsListed() {
        Division staffed = division(5, null);

        assertThat(listedIds()).contains(staffed.getId());
    }

    @Test
    @DisplayName("divisi tanpa karyawan tetap tampil kalau punya dataset")
    void divisionWithLiveDatasetIsListedWithoutEmployees() {
        // Pengecualian yang disengaja: tanpanya, dataset milik team yang
        // kebetulan kosong kehilangan tempat di daftar divisi.
        Division owner = division(0, null);
        dataset(owner, false);

        assertThat(listedIds()).contains(owner.getId());
    }

    @Test
    @DisplayName("dataset yang sudah dihapus tidak membuat divisi kosong tampil")
    void deletedDatasetDoesNotKeepDivisionListed() {
        // Dataset terhapus disaring di ON, bukan di HAVING. Kalau syarat itu
        // hilang, divisi ini tampil hanya karena pernah punya dataset.
        Division formerOwner = division(0, null);
        dataset(formerOwner, true);

        assertThat(listedIds()).doesNotContain(formerOwner.getId());
    }

    @Test
    @DisplayName("divisi yang tersembunyi tetap ditemukan pencocokan saat login")
    void hiddenDivisionIsStillMatchedByHrisTeamId() {
        // Inilah alasan barisnya dibiarkan hidup: karyawan baru di team yang
        // kini kosong harus langsung mendapat divisi.
        UUID teamId = UUID.randomUUID();
        Division empty = division(0, teamId);

        assertThat(listedIds()).doesNotContain(empty.getId());
        assertThat(divisionRepository.findByHrisTeamIdAndDeletedAtIsNull(teamId))
                .map(Division::getId)
                .contains(empty.getId());
    }
}
