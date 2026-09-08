package id.co.erdigma.satudata.modules.user.port.hris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.client.MockRestServiceServer;

import id.co.erdigma.satudata.config.CustomJwtAuthenticationConverter;
import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.HrisPermissionLevel;
import id.co.erdigma.satudata.enums.Role;
import id.co.erdigma.satudata.modules.user.port.EmployeeDirectory;
import id.co.erdigma.satudata.repository.UserRepository;

/**
 * Test integrasi untuk temuan review "provisioning JIT gagal commit di
 * transaksi read-only": bug itu hidup di kombinasi proxy transaksi
 * ({@code @Transactional} pada {@link CustomJwtAuthenticationConverter#convert})
 * dan koneksi JDBC sungguhan, jadi repository yang di-mock tidak akan pernah
 * menangkapnya — butuh konteks Spring nyata dan database nyata.
 *
 * jwk-set-uri ditimpa seperti di CognitoProfileContextTest supaya test ini
 * tidak butuh jaringan ke Cognito.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=dev",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.invalid/.well-known/jwks.json",
})
class HrisEmployeeDirectoryTest {

    @Autowired
    private CustomJwtAuthenticationConverter converter;

    @Autowired
    private EmployeeDirectory employeeDirectory;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MockServerHolder mockServerHolder;

    private final List<String> cognitoIdBuatanTest = new ArrayList<>();

    @BeforeEach
    void resetMockServer() {
        mockServerHolder.server.reset();
        cognitoIdBuatanTest.clear();
    }

    @AfterEach
    void hapusBarisBuatanTest() {
        // deleteAllInBatch memakai satu DELETE massal, jadi tidak lewat
        // @SQLDelete milik User (yang cuma mengisi deleted_at) — baris test
        // ini betul-betul hilang, bukan cuma ditandai terhapus.
        List<User> barisBuatanTest = cognitoIdBuatanTest.stream()
                .map(userRepository::findByCognitoId)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
        userRepository.deleteAllInBatch(barisBuatanTest);
    }

    @Test
    @DisplayName("provisioning lewat convert() betul-betul ter-commit ke database")
    void provisioningTerpersistDiDatabase() {
        String cognitoId = "it-test-" + UUID.randomUUID();
        cognitoIdBuatanTest.add(cognitoId);

        mockServerHolder.server.expect(requestTo(Matchers.endsWith("/user/me")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "email": "budi.santoso@erdigma.co.id",
                          "role": "STAFF",
                          "employee": {
                            "name": "Budi Santoso",
                            "jobLevel": "Staff",
                            "position": {"name": "Data Analyst"},
                            "team": {"id": "%s"}
                          }
                        }
                        """.formatted(UUID.randomUUID()), MediaType.APPLICATION_JSON));

        // Jalur produksi yang sesungguhnya: lewat proxy transaksi milik
        // converter, bukan langsung ke HrisEmployeeDirectory. Bug-nya justru
        // ada di readOnly milik method convert() ini.
        converter.convert(jwtFabrikasi(cognitoId));

        // Sengaja TIDAK membaca objek balikan convert(). Objek di memori bisa
        // saja terlihat benar padahal baris di database tidak pernah ada —
        // itulah yang menyembunyikan bug ini sebelumnya. Baca ulang query-nya
        // sendiri berjalan di transaksi barunya sendiri, terpisah dari
        // transaksi convert() yang sudah selesai (commit atau tidak) di atas.
        Optional<User> hasil = userRepository.findByCognitoId(cognitoId);

        assertThat(hasil).isPresent();
        assertThat(hasil.get().getName()).isEqualTo("Budi Santoso");
        assertThat(hasil.get().getRole()).isEqualTo(Role.STAFF);
        assertThat(hasil.get().getUpdatedAt()).isNotNull();

        mockServerHolder.server.verify();
    }

    @Test
    @DisplayName("401 dari hris-api menolak user dan tidak membuat baris")
    void balasan401MenolakUser() {
        String cognitoId = "it-test-" + UUID.randomUUID();
        cognitoIdBuatanTest.add(cognitoId);

        mockServerHolder.server.expect(requestTo(Matchers.endsWith("/user/me")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withUnauthorizedRequest());

        Optional<User> hasil = employeeDirectory.findByCognitoId(cognitoId, "token-tidak-berhak");

        assertThat(hasil).isEmpty();
        assertThat(userRepository.findByCognitoId(cognitoId)).isEmpty();
        mockServerHolder.server.verify();
    }

    @Test
    @DisplayName("hris-api tak terjangkau tetap memakai baris lokal yang basi")
    void kegagalanTransportMemakaiBarisBasi() {
        String cognitoId = "it-test-" + UUID.randomUUID();
        cognitoIdBuatanTest.add(cognitoId);

        // Ditruncate ke mikrodetik: kolom TIMESTAMP Postgres cuma menyimpan
        // presisi segitu, jadi LocalDateTime.now() yang masih bawa nanodetik
        // dibulatkan pulang-pergi dan gagal sama persis tanpa truncate ini.
        LocalDateTime waktuBasi = LocalDateTime.now().minusHours(13).truncatedTo(ChronoUnit.MICROS);
        User baris = new User();
        baris.setCognitoId(cognitoId);
        baris.setEmail("lama@erdigma.co.id");
        baris.setName("Nama Basi");
        baris.setRole(Role.STAFF);
        baris.setHrisPermissionLevel(HrisPermissionLevel.STAFF);
        baris.setJobLevel("Staff");
        baris.setUpdatedAt(waktuBasi);
        userRepository.save(baris);

        mockServerHolder.server.expect(requestTo(Matchers.endsWith("/user/me")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withException(new IOException("Simulasi HRIS mati")));

        Optional<User> hasil = employeeDirectory.findByCognitoId(cognitoId, "token-apa-saja");

        assertThat(hasil).isPresent();
        assertThat(hasil.get().getName()).isEqualTo("Nama Basi");
        assertThat(hasil.get().getUpdatedAt()).isEqualTo(waktuBasi);
        mockServerHolder.server.verify();
    }

    @Test
    @DisplayName("admin HRIS tanpa baris employee tetap diterima")
    void adminHrisTanpaEmployeeDiterima() {
        String cognitoId = "it-test-" + UUID.randomUUID();
        cognitoIdBuatanTest.add(cognitoId);

        mockServerHolder.server.expect(requestTo(Matchers.endsWith("/user/me")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "email": "engineer@erdigma.id",
                          "role": "ADMIN",
                          "employee": null
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<User> hasil = employeeDirectory.findByCognitoId(cognitoId, "token-admin");

        assertThat(hasil).isPresent();
        assertThat(hasil.get().getRole()).isEqualTo(Role.ADMIN);
        assertThat(hasil.get().getHrisPermissionLevel()).isEqualTo(HrisPermissionLevel.ADMIN);
        // Nama jatuh ke bagian depan email karena HRIS tidak punya karyawannya.
        assertThat(hasil.get().getName()).isEqualTo("engineer");
        assertThat(hasil.get().getJobLevel()).isNull();
        mockServerHolder.server.verify();
    }

    @Test
    @DisplayName("bukan admin dan tanpa employee tetap ditolak")
    void bukanAdminTanpaEmployeeDitolak() {
        String cognitoId = "it-test-" + UUID.randomUUID();
        cognitoIdBuatanTest.add(cognitoId);

        mockServerHolder.server.expect(requestTo(Matchers.endsWith("/user/me")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "email": "bukan.karyawan@erdigma.co.id",
                          "role": "USER",
                          "employee": null
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<User> hasil = employeeDirectory.findByCognitoId(cognitoId, "token-biasa");

        assertThat(hasil).isEmpty();
        assertThat(userRepository.findByCognitoId(cognitoId)).isEmpty();
        mockServerHolder.server.verify();
    }

    @Test
    @DisplayName("penyegaran HRIS tidak menimpa peran yang di-override")
    void overrideBertahanLewatPenyegaran() {
        String cognitoId = "it-test-" + UUID.randomUUID();
        cognitoIdBuatanTest.add(cognitoId);

        User baris = new User();
        baris.setCognitoId(cognitoId);
        baris.setEmail("staf@erdigma.co.id");
        baris.setName("Staf Ditunjuk");
        baris.setRole(Role.ADMIN);
        baris.setHrisPermissionLevel(HrisPermissionLevel.STAFF);
        baris.setRoleOverride(Role.ADMIN);
        baris.setRoleOverrideAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        // Basi, supaya penyegaran ke HRIS benar-benar dijalankan.
        baris.setUpdatedAt(LocalDateTime.now().minusHours(13).truncatedTo(ChronoUnit.MICROS));
        userRepository.save(baris);

        mockServerHolder.server.expect(requestTo(Matchers.endsWith("/user/me")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "email": "staf@erdigma.co.id",
                          "role": "USER",
                          "employee": {
                            "name": "Staf Ditunjuk",
                            "jobLevel": "Staff",
                            "position": {"name": "Data Analyst"}
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<User> hasil = employeeDirectory.findByCognitoId(cognitoId, "token-apa-saja");

        assertThat(hasil).isPresent();
        // Peran efektif tetap hasil tunjukan manusia...
        assertThat(hasil.get().getRole()).isEqualTo(Role.ADMIN);
        // ...sedangkan tingkat izin tetap apa kata HRIS.
        assertThat(hasil.get().getHrisPermissionLevel()).isEqualTo(HrisPermissionLevel.STAFF);
        mockServerHolder.server.verify();
    }

    @Test
    @DisplayName("admin warisan HRIS mendapat authority ROLE_HRIS_ADMIN")
    void adminWarisanDapatAuthorityKhusus() {
        String cognitoId = "it-test-" + UUID.randomUUID();
        cognitoIdBuatanTest.add(cognitoId);
        simpanBarisSegar(cognitoId, Role.ADMIN, HrisPermissionLevel.ADMIN);

        var authorities = converter.convert(jwtFabrikasi(cognitoId)).getAuthorities();

        assertThat(authorities).extracting("authority")
                .contains("ROLE_ADMIN", "ROLE_HRIS_ADMIN");
    }

    @Test
    @DisplayName("admin tunjukan tidak mendapat ROLE_HRIS_ADMIN")
    void adminTunjukanTidakDapatAuthorityKhusus() {
        String cognitoId = "it-test-" + UUID.randomUUID();
        cognitoIdBuatanTest.add(cognitoId);
        simpanBarisSegar(cognitoId, Role.ADMIN, HrisPermissionLevel.MANAGER);

        var authorities = converter.convert(jwtFabrikasi(cognitoId)).getAuthorities();

        assertThat(authorities).extracting("authority")
                .contains("ROLE_ADMIN")
                .doesNotContain("ROLE_HRIS_ADMIN");
    }

    @Test
    @DisplayName("admin HRIS yang diturunkan lewat override kehilangan ROLE_HRIS_ADMIN")
    void adminHrisDiturunkanKehilanganAuthorityKhusus() {
        String cognitoId = "it-test-" + UUID.randomUUID();
        cognitoIdBuatanTest.add(cognitoId);
        // Arah kebalikan dari adminTunjukanTidakDapatAuthorityKhusus: di sana
        // hrisPermissionLevel yang bukan ADMIN, di sini role yang diturunkan
        // override. Inilah yang membuat konjungsi berarti sesuatu — kalau
        // gerbangnya cuma hrisPermissionLevel == ADMIN, admin yang sudah
        // diturunkan lewat panel tetap bisa membuka manajemen pengguna.
        simpanBarisSegar(cognitoId, Role.STAFF, HrisPermissionLevel.ADMIN);

        var authorities = converter.convert(jwtFabrikasi(cognitoId)).getAuthorities();

        assertThat(authorities).extracting("authority")
                .contains("ROLE_STAFF")
                .doesNotContain("ROLE_HRIS_ADMIN");
    }

    /** Baris dengan updatedAt sekarang: masihSegar() memotong panggilan HRIS. */
    private void simpanBarisSegar(String cognitoId, Role role, HrisPermissionLevel level) {
        User baris = new User();
        baris.setCognitoId(cognitoId);
        baris.setEmail(cognitoId + "@erdigma.co.id");
        baris.setName("Uji Authority");
        baris.setRole(role);
        baris.setHrisPermissionLevel(level);
        baris.setUpdatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        userRepository.save(baris);
    }

    private static Jwt jwtFabrikasi(String cognitoId) {
        Instant sekarang = Instant.now();
        return Jwt.withTokenValue("token-fabrikasi-tidak-perlu-valid")
                .header("alg", "none")
                .claim("sub", cognitoId)
                .issuedAt(sekarang)
                .expiresAt(sekarang.plusSeconds(3600))
                .build();
    }

    /**
     * Inilah yang rusak sebelum changeset 41-42, dan rusaknya diam-diam.
     *
     * Divisi dicocokkan lewat `division.hris_team_id`. Dulu kolomnya bernama
     * `hris_departement_id` dan KOSONG untuk kedelapan divisi desain, jadi
     * pencarian tidak pernah ketemu dan setiap pengguna Cognito berdivisi null.
     * Akibatnya mereka tidak bisa menerbitkan dataset sama sekali —
     * DatasetUploadService menolak karena tidak ada yang bisa dicatat sebagai
     * penerbit.
     *
     * Tidak ada galat yang muncul waktu itu: `ifPresent` yang tidak pernah
     * berjalan terlihat persis sama dengan yang berhasil.
     */
    @Test
    @DisplayName("team dari HRIS dipetakan ke divisi lewat hris_team_id")
    void hrisTeamBecomesDivision() {
        String cognitoId = "it-test-" + UUID.randomUUID();
        cognitoIdBuatanTest.add(cognitoId);

        // Id team "Data & IT" milik hris-api, sama dengan yang diseed
        // changeset 42. Sengaja ditulis apa adanya: kalau seed-nya berubah,
        // tes inilah yang harus ikut dibaca ulang.
        String dataAndItTeamId = "b59dd564-ec63-48f3-9195-89b05a1b0284";

        mockServerHolder.server.expect(requestTo(Matchers.endsWith("/user/me")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "email": "sinta.dev@erdigma.co.id",
                          "role": "STAFF",
                          "employee": {
                            "name": "Sinta Dev",
                            "jobLevel": "Staff",
                            "position": {"name": "Data Analyst"},
                            "team": {"id": "%s"}
                          }
                        }
                        """.formatted(dataAndItTeamId), MediaType.APPLICATION_JSON));

        converter.convert(jwtFabrikasi(cognitoId));

        Optional<User> hasil = userRepository.findByCognitoId(cognitoId);
        assertThat(hasil).isPresent();
        assertThat(hasil.get().getDivision())
                .as("divisi harus terisi, bukan null seperti sebelum changeset 41")
                .isNotNull();

        // Sengaja membandingkan id, bukan getCode(). Relasi divisi dimuat malas,
        // dan di luar transaksi Hibernate proxy-nya tidak bisa diisi lagi —
        // getCode() akan melempar LazyInitializationException. Id adalah
        // satu-satunya ruas yang tersedia tanpa membuka sesi baru.
        assertThat(hasil.get().getDivision().getId())
                .as("baris divisi Data & IT yang diseed changeset 42")
                .hasToString("b0000000-0000-4000-8000-000000000005");

        mockServerHolder.server.verify();
    }

    /**
     * Team yang belum ada di tabel `division` tidak boleh membuat provisioning
     * gagal — orangnya tetap masuk, hanya tanpa divisi. Ini yang terjadi kalau
     * Erdigma menambah team baru di HRIS sebelum seed-nya diperbarui.
     */
    @Test
    @DisplayName("team yang tidak dikenal membuat divisi null, bukan galat")
    void unknownTeamLeavesDivisionNull() {
        String cognitoId = "it-test-" + UUID.randomUUID();
        cognitoIdBuatanTest.add(cognitoId);

        mockServerHolder.server.expect(requestTo(Matchers.endsWith("/user/me")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "email": "team.baru@erdigma.co.id",
                          "role": "STAFF",
                          "employee": {
                            "name": "Team Baru",
                            "jobLevel": "Staff",
                            "position": {"name": "Staff"},
                            "team": {"id": "%s"}
                          }
                        }
                        """.formatted(UUID.randomUUID()), MediaType.APPLICATION_JSON));

        converter.convert(jwtFabrikasi(cognitoId));

        Optional<User> hasil = userRepository.findByCognitoId(cognitoId);
        assertThat(hasil).isPresent();
        assertThat(hasil.get().getDivision()).isNull();

        mockServerHolder.server.verify();
    }

    static class MockServerHolder {
        private MockRestServiceServer server;
    }

    /**
     * HrisEmployeeDirectory membangun RestClient-nya sendiri di konstruktor,
     * jadi MockRestServiceServer.bindTo(...) di dalam method test sudah
     * terlambat — builder itu sudah ter-build() sebelum test sempat jalan.
     * RestClientCustomizer adalah titik yang dipanggil Spring saat builder
     * auto-configured itu masih dibentuk, sebelum dipakai HrisEmployeeDirectory.
     *
     * @AutoConfigureMockRestServiceServer tidak dipakai karena kelas itu (dan
     * MockServerRestClientCustomizer pasangannya) sudah tidak ada di Spring
     * Boot 4.1 — sudah diverifikasi tidak ada di jar spring-boot-test-autoconfigure
     * maupun spring-boot-resttestclient versi ini.
     */
    @TestConfiguration
    static class MockHrisServerConfig {

        @Bean
        MockServerHolder mockServerHolder() {
            return new MockServerHolder();
        }

        @Bean
        RestClientCustomizer hrisMockingCustomizer(MockServerHolder holder) {
            return builder -> holder.server = MockRestServiceServer.bindTo(builder).build();
        }
    }
}
