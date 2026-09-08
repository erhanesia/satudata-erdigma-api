package id.co.erdigma.satudata.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Satu klien HTTP untuk seluruh panggilan ke hris-api.
 *
 * Dulu tiap kelas membangunnya sendiri dari {@code RestClient.Builder}. Itu
 * bekerja sampai ada kelas kedua, lalu memunculkan dua persoalan sekaligus.
 *
 * Yang pertama terlihat di tes. {@code MockRestServiceServer.bindTo(builder)}
 * mengikat mock ke SATU builder, dan {@code RestClientCustomizer} berjalan untuk
 * setiap builder yang dibuat — sehingga klien kedua diam-diam menggantikan mock
 * milik klien pertama, dan tes yang tadinya lulus mulai menjawab
 * "No further requests expected".
 *
 * Yang kedua akan terlihat nanti. Setelan seperti batas waktu, percobaan ulang,
 * atau header bersama harus dipasang di setiap tempat yang membangun klien, dan
 * yang terlewat baru ketahuan saat HRIS lambat di produksi.
 *
 * Dengan satu bean, keduanya selesai: satu tempat menyetel, satu klien untuk
 * diikat mock.
 */
@Configuration
public class HrisClientConfig {

    @Bean
    public RestClient hrisRestClient(RestClient.Builder builder, @Value("${hris.base-url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
