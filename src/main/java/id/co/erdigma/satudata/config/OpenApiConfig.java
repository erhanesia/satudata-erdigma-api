package id.co.erdigma.satudata.config;

import java.util.Arrays;
import java.util.List;

import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import id.co.erdigma.satudata.annotation.CurrentUser;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    /** Nama skema JWT — dipakai saat Cognito sudah tersambung. */
    private static final String BEARER_SCHEME = "bearerAuth";

    /** Nama skema header dummy — hanya hidup di profil auth-dummy. */
    private static final String DUMMY_SCHEME = "dummyAuth";

    private final Environment environment;

    public OpenApiConfig(Environment environment) {
        this.environment = environment;
    }

    static {
        // Parameter @CurrentUser diisi dari token, bukan dari query string.
        // Tanpa baris ini springdoc mendokumentasikannya sebagai parameter
        // bernama "user" dan menyeret entity User + Division ke skema publik.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUser.class);
    }

    /**
     * Mendaftarkan skema keamanan agar tombol "Authorize" muncul di Swagger UI.
     *
     * Tanpa ini seluruh endpoint terdokumentasi seolah terbuka, dan setiap
     * percobaan lewat "Try it out" dijawab 401 tanpa penjelasan — filter
     * keamanannya bekerja, tapi Swagger tidak punya tempat untuk memasukkan
     * kredensial.
     *
     * Dua skema didaftarkan sesuai profil yang menyala, mengikuti pilihan yang
     * sama di {@link SecurityConfig}: header dummy saat pengembangan lokal,
     * Bearer JWT saat Cognito aktif.
     */
    @Bean
    public OpenAPI satudataOpenAPI() {
        boolean authDummy = Arrays.asList(environment.getActiveProfiles()).contains("auth-dummy");

        Components components = new Components()
                .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token akses Cognito. Belum aktif selama profil auth-dummy menyala."));

        OpenAPI openApi = new OpenAPI()
                .info(new Info()
                        .title("Satu Data Erdigma API")
                        .description(description(authDummy))
                        .version("v1"))
                .components(components);

        if (authDummy) {
            components.addSecuritySchemes(DUMMY_SCHEME, new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name(DummyAuthFilter.COGNITO_SUB_HEADER)
                    .description("Identitas uji lokal. Nilai: dummy-admin, dummy-director, "
                            + "dummy-corpsec, dummy-manager, dummy-specialist, dummy-staff, "
                            + "dummy-resigned (sengaja ditolak 403)."));

            // Skema dummy diletakkan lebih dulu supaya jadi pilihan pertama
            // pada dialog Authorize saat pengembangan.
            openApi.security(List.of(
                    new SecurityRequirement().addList(DUMMY_SCHEME),
                    new SecurityRequirement().addList(BEARER_SCHEME)));
        } else {
            openApi.security(List.of(new SecurityRequirement().addList(BEARER_SCHEME)));
        }

        return openApi;
    }

    private String description(boolean authDummy) {
        String base = """
                Portal data internal PT Erdigma — katalog dataset antar-divisi.

                ---

                ### Mulai dari mana

                1. **Tekan Authorize** (tombol gembok di kanan atas) dan isi kredensialnya —
                   caranya di bawah. Tanpa ini semua endpoint menjawab **401**.
                2. **Coba `GET /api/v1/me` lebih dulu.** Kalau menjawab 200, kredensial Anda benar
                   dan endpoint lain pasti bisa dipanggil. Ini cara tercepat memastikan Authorize
                   berhasil sebelum menyalahkan endpoint lain.
                3. **Panggil `GET /api/v1/datasets`** tanpa mengisi parameter apa pun untuk melihat
                   seluruh dataset. Ambil nilai `slug` dari hasilnya.
                4. **Pakai slug itu** di endpoint `/api/v1/datasets/{slug}/…` untuk metadata, isi
                   tabel, grafik, atau unduhan.

                ### Aturan yang berlaku di seluruh endpoint

                - **Identitas di URL adalah `slug`, bukan UUID.** Contoh: `penjualan-furnitur-2025`,
                  bukan `e0000000-…`. Slug adalah versi judul yang aman dipakai di URL. Pengecualian
                  hanya API key, yang memakai UUID karena tidak punya nama publik.
                - **Halaman dimulai dari 0**, bukan 1. `page=0` berarti halaman pertama.
                - **`size` minimal 1.** Mengisi 0 dijawab **400**, bukan daftar kosong.
                - **Parameter penyaring yang dikosongkan tidak dikirim** — itu berarti "jangan
                  saring", bukan "saring dengan nilai kosong".
                - **Daftar bisa diisi berulang.** Untuk dua topik, isi `topics` dua baris terpisah,
                  bukan satu baris berisi keduanya.

                ### Arti kode balasan

                | Kode | Artinya |
                |---|---|
                | 200 | Berhasil |
                | 400 | Isian Anda tidak masuk akal — pesan aslinya ada di badan respons |
                | 401 | Belum Authorize, atau identitasnya tidak dikenal |
                | 403 | Terautentikasi, tapi karyawannya sudah resign |
                | 404 | Slug atau id-nya tidak ada |

                ### Yang perlu diketahui soal data

                Dari 9 dataset di katalog, **hanya `penjualan-furnitur-2025` yang punya isi tabel
                dan berkas** (10.000 baris, 17 kolom). Delapan lainnya baru berisi metadata, jadi
                `/datastore` mereka kosong dan `/download` menjawab 404 — itu perilaku yang benar,
                bukan kerusakan.
                """;
        if (!authDummy) {
            return base + """

                    ### Autentikasi

                    Tekan **Authorize**, pilih `bearerAuth`, lalu tempel token akses Cognito.
                    """;
        }
        return base + """

                ### Autentikasi — mode pengembangan (profil `auth-dummy`)

                Tekan **Authorize**, isi skema **`dummyAuth`** (yang paling atas) dengan salah satu
                identitas uji di bawah, lalu Close. Biarkan `bearerAuth` kosong — skema itu baru
                hidup setelah Cognito tersambung.

                Nilainya dikirim sebagai header `""" + DummyAuthFilter.COGNITO_SUB_HEADER + """
                `.

                | Isi `dummyAuth` | Peran portal | Izin HRIS |
                |---|---|---|
                | `dummy-admin` | ADMIN | ADMIN |
                | `dummy-director` | ADMIN | DIRECTOR |
                | `dummy-corpsec` | PUBLISHER | CORPORATE_SECRETARY |
                | `dummy-manager` | PUBLISHER | MANAGER |
                | `dummy-specialist` | STAFF | STAFF |
                | `dummy-staff` | STAFF | STAFF |
                | `dummy-resigned` | — | **sengaja ditolak 403** |

                Untuk berganti identitas: Authorize → **Logout** → isi nilai baru.
                """;
    }
}
