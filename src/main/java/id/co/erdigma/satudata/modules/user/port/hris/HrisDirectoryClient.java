package id.co.erdigma.satudata.modules.user.port.hris;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import id.co.erdigma.satudata.exception.BusinessValidationException;

import lombok.extern.slf4j.Slf4j;

/**
 * Membaca daftar posisi dan karyawan dari hris-api untuk mengisi pemilih pada
 * kartu "Siapa yang boleh melihat".
 *
 * <h2>Kenapa diteruskan, bukan diseed</h2>
 *
 * Daftar posisi berisi 98 baris dan daftar karyawan 512, dan keduanya berubah
 * tanpa memberi tahu siapa pun. Menyalinnya ke tabel sendiri mengulangi persoalan
 * yang sudah terjadi pada divisi: salinan yang dibuat 27 Agustus sudah tidak
 * cocok lagi keesokan harinya.
 *
 * Berbeda dari sinkronisasi divisi yang terhalang kredensial mesin, pemilihan di
 * sini SELALU dilakukan admin yang sedang login. Tokennya ada, dan hris-api
 * menilai izinnya persis seperti saat orang itu membuka HRIS sendiri.
 *
 * <h2>Token diambil dari SecurityContext</h2>
 *
 * Bukan diteruskan sebagai parameter seperti pada {@link HrisEmployeeDirectory}.
 * Di sana tokennya harus dioper karena converter dipanggil SEBELUM
 * SecurityContext terisi — ia justru yang sedang mengisinya. Di sini
 * permintaannya sudah lewat rantai keamanan, jadi tokennya sudah tersedia.
 */
@Component
@Slf4j
public class HrisDirectoryClient {

    /**
     * Baris uji coba di tabel posisi HRIS, per 4 September 2026: sebelas
     * "DUMMY DELETE - Position N", satu "Test Baru", satu "Finance Baru".
     *
     * Disaring di sini supaya tidak muncul di pemilih akses. Ini menutupi, bukan
     * menyelesaikan — yang benar adalah membersihkannya di HRIS, dan itu sudah
     * dicatat sebagai hal yang perlu disampaikan ke pemilik sistem sana.
     */
    private static final List<String> TEST_DATA_PREFIXES = List.of("dummy delete", "test baru", "finance baru");

    /** Tanpa batas, satu permintaan bisa menarik ratusan baris sekaligus. */
    private static final int MAX_PAGE_SIZE = 100;

    private final RestClient hris;

    public HrisDirectoryClient(RestClient hrisRestClient) {
        this.hris = hrisRestClient;
    }

    /**
     * Kata kunci boleh kosong: 98 posisi masih wajar dimuat sekaligus, dan
     * antarmuka menyaringnya sendiri di sisi klien.
     */
    public List<HrisRefResponse.Item> positions(String search, int size) {
        return fetchPage("/position", search, size).stream()
                .filter(item -> !isTestData(item.getName()))
                .toList();
    }

    /**
     * Kata kunci WAJIB, berbeda dari posisi.
     *
     * Ada 512 karyawan, dan memuat semuanya untuk sebuah pemilih berarti
     * mengirim daftar yang tidak akan dibaca siapa pun sampai habis. Mewajibkan
     * kata kunci memaksa antarmuka menampilkan kotak pencarian, dan itu memang
     * satu-satunya cara memakai daftar sebesar ini.
     */
    public List<HrisRefResponse.Item> employees(String search, int size) {
        if (search == null || search.isBlank()) {
            throw new BusinessValidationException(
                    "Kata kunci pencarian wajib diisi untuk mencari karyawan.");
        }
        return fetchPage("/employee", search, size);
    }

    /**
     * Menerjemahkan satu id karyawan menjadi namanya.
     *
     * Dipakai antarmuka untuk menampilkan aturan bertipe EMPLOYEE yang sudah
     * tersimpan. Tanpa ini yang terbaca di layar adalah UUID mentah, dan tidak
     * ada seorang pun yang bisa menilai apakah pembatasan sebuah dataset sudah
     * benar dengan membaca deretan UUID.
     *
     * Sengaja satu-per-id dan bukan pencarian massal: hris-api tidak punya
     * endpoint "ambil banyak sekaligus", dan aturan EMPLOYEE pada sebuah dataset
     * jumlahnya satuan. Yang penting jalur ini TIDAK dipakai di halaman daftar —
     * di sana kolomnya hanya menampilkan jumlah, justru supaya lima puluh baris
     * tidak berubah jadi lima puluh panggilan.
     *
     * Mengembalikan kosong kalau HRIS tidak mengenali id-nya. Itu keadaan yang
     * wajar: karyawan bisa saja sudah dihapus setelah aturannya dibuat, dan
     * antarmuka menampilkan UUID-nya apa adanya alih-alih gagal.
     */
    public Optional<HrisRefResponse.Item> employee(UUID id) {
        try {
            HrisRefResponse.Item item = hris.get()
                    .uri("/employee/{id}", id)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + callerToken())
                    .retrieve()
                    .body(HrisRefResponse.Item.class);
            return Optional.ofNullable(item);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("hris-api tidak dapat dihubungi untuk /employee/{} ({})", id, e.getMessage());
            throw new BusinessValidationException(
                    "Data karyawan dari HRIS sedang tidak bisa diambil. Coba lagi sebentar lagi.");
        }
    }

    private List<HrisRefResponse.Item> fetchPage(String path, String search, int size) {
        int cappedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        try {
            HrisRefResponse response = hris.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(path).queryParam("size", cappedSize);
                        if (search != null && !search.isBlank()) {
                            uriBuilder.queryParam("search", search.trim());
                        }
                        return uriBuilder.build();
                    })
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + callerToken())
                    .retrieve()
                    .body(HrisRefResponse.class);

            return (response == null || response.getContent() == null)
                    ? List.of()
                    : response.getContent();
        } catch (RestClientException e) {
            // Daftar kosong akan terbaca sebagai "tidak ada posisi bernama itu",
            // dan penerbit akan mengira pencariannya yang salah. Lebih jujur
            // menyatakan bahwa HRIS-nya yang tidak bisa dihubungi.
            log.warn("hris-api tidak dapat dihubungi untuk {} ({})", path, e.getMessage());
            throw new BusinessValidationException(
                    "Daftar dari HRIS sedang tidak bisa diambil. Coba lagi sebentar lagi.");
        }
    }

    private boolean isTestData(String name) {
        if (name == null) {
            return false;
        }
        String cleaned = name.trim().toLowerCase(Locale.ROOT);
        return TEST_DATA_PREFIXES.stream().anyMatch(cleaned::startsWith);
    }

    private String callerToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getTokenValue();
        }
        // Tidak akan terjadi lewat jalur HTTP biasa: seluruh endpoint di sini
        // butuh autentikasi. Melempar agar kegagalannya berisik, bukan berujung
        // panggilan tanpa token yang dijawab HRIS dengan 401 yang membingungkan.
        throw new IllegalStateException("Token pemanggil tidak tersedia di SecurityContext.");
    }
}
