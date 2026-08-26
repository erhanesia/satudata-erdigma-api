package id.co.erdigma.satudata.service.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import id.co.erdigma.satudata.modules.dataset.service.DatasetImportService;

import software.amazon.awssdk.services.s3.S3Client;

/**
 * Membuktikan seam FileStorage benar-benar tersambung lewat Spring saat
 * provider=S3 — bukan cuma dites lewat pemanggilan konstruktor langsung
 * seperti {@link S3FileStorageTest}, dan bukan cuma dites manual sekali saat
 * boot dengan STORAGE_PROVIDER=S3.
 *
 * {@link id.co.erdigma.satudata.SatudataApplicationTests} sengaja memin
 * provider ke LOCAL supaya konteks itu tidak pernah menyentuh grup bean S3.
 * Tes ini melengkapi, bukan menggantikan: memuat konteks dengan provider=S3
 * sungguhan, lalu memeriksa bean apa yang sebenarnya disuntikkan. Tanpa ini,
 * regresi seperti field @Autowired bertipe S3FileStorage (kebocoran kelas
 * konkret) atau perubahan tanda tangan bean di S3Config yang merusak
 * penyuntikan tidak akan tertangkap satu tes pun.
 */
@SpringBootTest(properties = "satudata.storage.provider=S3")
class S3ProviderWiringTest {

    /**
     * S3Client asli diganti bean tiruan Mockito supaya konteks tidak pernah
     * menyentuh jaringan atau butuh kredensial AWS. Ini juga membuat
     * {@code @PostConstruct} milik S3FileStorage (pemeriksaan headBucket) aman:
     * mock mengembalikan nilai kosong tanpa melempar, bukan memanggil AWS.
     */
    @MockitoBean
    private S3Client s3Client;

    @Autowired
    private FileStorage fileStorage;

    @Autowired
    private DatasetImportService datasetImportService;

    @Test
    @DisplayName("Bean FileStorage yang disediakan Spring adalah S3FileStorage ketika provider=S3")
    void beanFileStorageAdalahS3FileStorage() {
        // ultimateTargetClass dipakai, bukan isInstanceOf, supaya tes ini
        // tetap benar walau Spring suatu saat membungkus bean-nya dengan proxy.
        assertThat(AopProxyUtils.ultimateTargetClass(fileStorage)).isEqualTo(S3FileStorage.class);
    }

    @Test
    @DisplayName("DatasetImportService menyuntik S3FileStorage yang sama, bukan implementasi lain")
    void datasetImportServiceMenyuntikS3FileStorage() {
        // DatasetImportService memakai injeksi field privat (@Autowired
        // langsung di field), jadi diperiksa lewat refleksi, bukan getter.
        Object suntikan = ReflectionTestUtils.getField(datasetImportService, "fileStorage");

        assertThat(suntikan).isNotNull();
        assertThat(AopProxyUtils.ultimateTargetClass(suntikan)).isEqualTo(S3FileStorage.class);
    }
}
