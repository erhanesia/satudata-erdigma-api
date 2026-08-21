package id.co.erdigma.satudata.modules.user.port.hris;

import java.time.Duration;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Memasang timeout koneksi/baca pada {@link RestClient.Builder} yang
 * dipakai {@link HrisEmployeeDirectory}.
 *
 * BUKAN lewat properti {@code application.yaml}: {@code spring.http.client.*}
 * (tunggal) BUKAN kunci yang valid di Spring Boot 4.1 — sudah diverifikasi
 * lewat bytecode {@code HttpClientsProperties}, prefiksnya {@code
 * spring.http.clients} (jamak, tanpa nested "client"). Properti itu juga
 * berlaku global ke semua RestClient/RestTemplate lain, bukan cuma klien HRIS
 * ini.
 *
 * Tanpa timeout ini, {@code hris.get()} di HrisEmployeeDirectory yang macet
 * (bukan gagal cepat — overload, jeda GC, koneksi setengah terbuka lewat load
 * balancer) menggantung permintaan itu SELAMANYA di dalam filter keamanan.
 * Kolam worker Tomcat habis dan portal ini ikut mati — persis yang coba
 * dicegah fallback "HRIS mati" di HrisEmployeeDirectory#findByCognitoId, yang
 * cuma menyelamatkan kegagalan cepat semacam connection refused.
 *
 * Ini {@link RestClientCustomizer} terpisah, BUKAN kode langsung di
 * constructor HrisEmployeeDirectory: constructor itu membangun RestClient-nya
 * sendiri, dan apa pun yang dipanggilnya di builder berjalan SESUDAH semua
 * customizer — jadi kode langsung di sana akan selalu menimpa request factory
 * mana pun yang dipasang test lewat MockRestServiceServer. Order paling
 * duluan di sini justru supaya customizer TANPA urutan eksplisit (mis. punya
 * test) tetap dipanggil BELAKANGAN dalam bean {@code restClientBuilder()} yang
 * sama dan menang menimpa factory ini — lihat HrisEmployeeDirectoryTest.
 *
 * ponytail: berlaku ke SEMUA RestClient.Builder auto-configured, bukan
 * dibatasi ke klien HRIS lewat baseUrl — baru ada satu konsumen
 * (HrisEmployeeDirectory) sekarang. Kalau nanti ada RestClient lain dengan
 * kebutuhan timeout berbeda, pisahkan lewat qualifier atau builder khusus.
 */
@Component
@Profile("!auth-dummy")
@Order(Ordered.HIGHEST_PRECEDENCE)
class HrisRestClientTimeoutCustomizer implements RestClientCustomizer {

    @Override
    public void customize(RestClient.Builder builder) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(Duration.ofSeconds(5));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        builder.requestFactory(requestFactory);
    }
}
