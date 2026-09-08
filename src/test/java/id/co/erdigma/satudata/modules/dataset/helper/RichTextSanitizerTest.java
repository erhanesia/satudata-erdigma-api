package id.co.erdigma.satudata.modules.dataset.helper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Mengunci apa yang boleh lolos ke deskripsi dataset, dan apa yang tidak.
 *
 * <h2>Kenapa kelas ini paling menentukan di antara tes-tes lain</h2>
 *
 * Deskripsi dataset ditulis satu orang dan dibaca banyak orang. Satu potong
 * skrip yang lolos ke sini akan dijalankan di peramban SETIAP karyawan yang
 * membuka dataset itu, dengan sesi mereka masing-masing — termasuk sesi admin.
 *
 * Kegagalannya juga tidak menghasilkan galat apa pun. Halaman tetap tergambar,
 * deskripsinya tetap terbaca, dan tidak ada satu pun catatan yang menyebut
 * bahwa sesuatu yang lain ikut berjalan.
 *
 * <h2>Yang diuji bukan hanya "tag script dibuang"</h2>
 *
 * Membuang {@code <script>} adalah bagian yang paling mudah dan paling jarang
 * jadi sebab kebocoran. Yang lebih sering lolos: penangan kejadian pada elemen
 * yang tampak polos, {@code javascript:} pada tautan, dan bentuk-bentuk yang
 * sengaja ditulis rusak supaya penyaring naif membacanya berbeda dari peramban.
 */
class RichTextSanitizerTest {

    private final RichTextSanitizer sanitizer = new RichTextSanitizer();

    // ------------------------------------------------------------- ditahan

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "<SCRIPT>alert(1)</SCRIPT>",
            "<img src=x onerror=alert(1)>",
            "<p onclick=\"alert(1)\">halo</p>",
            "<p onmouseover='alert(1)'>halo</p>",
            "<iframe src=\"https://jahat.example\"></iframe>",
            "<object data=\"jahat.swf\"></object>",
            "<embed src=\"jahat.swf\">",
            "<svg><script>alert(1)</script></svg>",
            "<svg onload=alert(1)>",
            "<style>body{display:none}</style>",
            "<link rel=stylesheet href=\"https://jahat.example/a.css\">",
            "<meta http-equiv=\"refresh\" content=\"0;url=https://jahat.example\">",
            "<base href=\"https://jahat.example/\">",
            "<form action=\"https://jahat.example\"><input name=p></form>",
    })
    @DisplayName("tidak ada satu pun yang bisa dieksekusi tersisa")
    void nothingExecutableSurvives(String jahat) {
        String hasil = sanitizer.sanitize(jahat);

        assertThat(hasil.toLowerCase())
                .doesNotContain("<script")
                .doesNotContain("onerror")
                .doesNotContain("onclick")
                .doesNotContain("onmouseover")
                .doesNotContain("onload")
                .doesNotContain("<iframe")
                .doesNotContain("<object")
                .doesNotContain("<embed")
                .doesNotContain("<svg")
                .doesNotContain("<style")
                .doesNotContain("<link")
                .doesNotContain("<meta")
                .doesNotContain("<base")
                .doesNotContain("<form");
    }

    @Test
    @DisplayName("tautan javascript: dibuang, tulisannya tetap tinggal")
    void javascriptLinksAreStripped() {
        /*
         * Bentuk paling tua dan paling sering terlupakan. Tautannya terlihat
         * biasa saja di layar, dan kodenya baru berjalan saat ditekan — jauh
         * setelah siapa pun sempat menghubungkannya dengan deskripsi dataset.
         */
        String hasil = sanitizer.sanitize("<a href=\"javascript:alert(1)\">tekan saya</a>");

        assertThat(hasil.toLowerCase()).doesNotContain("javascript:");
        // Tulisannya tidak ikut hilang: yang berbahaya alamatnya, bukan katanya.
        assertThat(hasil).contains("tekan saya");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "<a href=\"JaVaScRiPt:alert(1)\">x</a>",
            "<a href=\"  javascript:alert(1)\">x</a>",
            "<a href=\"data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==\">x</a>",
            "<a href=\"vbscript:msgbox(1)\">x</a>",
    })
    @DisplayName("penyamaran pada alamat tautan ikut ditolak")
    void disguisedLinkProtocolsAreRejected(String jahat) {
        // Huruf besar-kecil dicampur, spasi di depan, dan protokol lain yang
        // sama-sama bisa menjalankan kode. Penyaring yang cuma membandingkan
        // string "javascript:" akan meloloskan ketiganya.
        String hasil = sanitizer.sanitize(jahat).toLowerCase();

        assertThat(hasil).doesNotContain("javascript");
        assertThat(hasil).doesNotContain("vbscript");
        assertThat(hasil).doesNotContain("data:text/html");
    }

    @Test
    @DisplayName("atribut style dibuang, karena CSS pun bisa menutupi halaman")
    void styleAttributesAreStripped() {
        // Bukan soal kerapian. CSS bisa menutupi seluruh halaman dengan satu
        // elemen, memindahkan tombol ke tempat lain, atau memuat alamat luar.
        String hasil = sanitizer.sanitize(
                "<p style=\"position:fixed;inset:0;background:red\">halo</p>");

        assertThat(hasil).doesNotContain("style");
        assertThat(hasil).contains("halo");
    }

    @Test
    @DisplayName("gambar tidak diizinkan meski editornya tidak menawarkannya")
    void imagesAreNotAllowed() {
        /*
         * Editornya memang tidak punya tombol gambar, tetapi itu keputusan
         * antarmuka — dan antarmuka bisa dilewati dengan memanggil API-nya
         * langsung. Larangan di sini yang membuatnya benar-benar tidak bisa
         * masuk.
         */
        assertThat(sanitizer.sanitize("<img src=\"https://contoh.example/a.png\">"))
                .doesNotContain("<img");
        assertThat(sanitizer.sanitize("<img src=\"data:image/png;base64,iVBORw0KGgo=\">"))
                .doesNotContain("<img");
    }

    // ---------------------------------------------------------- diloloskan

    @Test
    @DisplayName("penekanan kata dan daftar tetap utuh")
    void ordinaryFormattingSurvives() {
        String masuk = "<p>Rekap <strong>penjualan</strong> dan <em>target</em>.</p>"
                + "<ul><li>Januari</li><li>Februari</li></ul>";

        assertThat(sanitizer.sanitize(masuk))
                .contains("<strong>penjualan</strong>")
                .contains("<em>target</em>")
                .contains("<li>Januari</li>");
    }

    @Test
    @DisplayName("satu tingkat judul yang lolos; tingkat lain kehilangan tag-nya")
    void onlyOneHeadingLevelSurvives() {
        /*
         * Editornya cuma menawarkan satu tombol judul, tetapi tempelan dari Word
         * dan permintaan yang dikirim langsung ke API tidak melewati editor itu.
         * Yang menegakkan batasnya kelas ini.
         *
         * Tingkat lain TIDAK ditolak mentah-mentah: tulisannya tetap tinggal
         * sebagai teks biasa. Yang salah bentuknya, bukan isinya, dan membuang
         * kalimat orang karena ia menempelkannya dengan gaya yang keliru adalah
         * hukuman yang tidak sepadan.
         */
        assertThat(sanitizer.sanitize("<h2>Ringkasan</h2>")).contains("<h2>Ringkasan</h2>");

        for (String tingkatLain : new String[] { "h1", "h3", "h4", "h5", "h6" }) {
            String hasil = sanitizer.sanitize(
                    "<" + tingkatLain + ">Judul</" + tingkatLain + ">");
            assertThat(hasil).doesNotContain("<" + tingkatLain);
            assertThat(hasil).contains("Judul");
        }
    }

    @Test
    @DisplayName("tautan biasa lolos dan dipasangi rel pengaman")
    void ordinaryLinksSurviveWithSafeRel() {
        // `noopener` yang menentukan: tanpa itu halaman tujuan bisa mengarahkan
        // ulang tab asalnya ke halaman masuk palsu, dan orangnya tidak melihat
        // apa pun yang janggal karena tabnya memang tab yang ia buka sendiri.
        String hasil = sanitizer.sanitize("<a href=\"https://erdigma.co.id\">Situs kami</a>");

        assertThat(hasil).contains("https://erdigma.co.id");
        assertThat(hasil).contains("noopener");
        assertThat(hasil).contains("noreferrer");
    }

    @Test
    @DisplayName("teks biasa tanpa satu pun tag lolos apa adanya")
    void plainTextPassesThrough() {
        // Deskripsi lama ditulis sebelum editor teks kaya ada, dan seluruhnya
        // teks polos. Kalau pembersih ini merusaknya, seluruh dataset lama
        // kehilangan deskripsinya begitu disunting sekali.
        assertThat(sanitizer.sanitize("Rekap penjualan furnitur 2025"))
                .isEqualTo("Rekap penjualan furnitur 2025");
    }

    @Test
    @DisplayName("karakter khusus pada teks biasa di-escape, bukan dibuang")
    void specialCharactersAreEscaped() {
        // "Laba > 5%" harus tetap terbaca "Laba > 5%" di layar. Yang berubah
        // cuma cara menuliskannya di HTML.
        String hasil = sanitizer.sanitize("Laba > 5% & tumbuh");

        assertThat(hasil).contains("&gt;").contains("&amp;");
    }

    // ------------------------------------------------------------- kosong

    @Test
    @DisplayName("paragraf kosong dari editor dihitung sebagai kosong")
    void emptyEditorContentBecomesEmpty() {
        /*
         * Editor teks kaya menyimpan "tidak ada isi" sebagai <p><br></p>.
         * Dibiarkan apa adanya, dataset yang deskripsinya sudah dikosongkan
         * tetap terhitung punya deskripsi — sehingga kalimat "Belum ada
         * deskripsi" tidak pernah muncul dan yang tampil kotak kosong tanpa
         * penjelasan.
         */
        assertThat(sanitizer.sanitize("<p><br></p>")).isEmpty();
        assertThat(sanitizer.sanitize("<p></p>")).isEmpty();
        assertThat(sanitizer.sanitize("<p>&nbsp;</p>")).isEmpty();
        assertThat(sanitizer.sanitize("   ")).isEmpty();
    }

    @Test
    @DisplayName("null tetap null, bukan berubah jadi string kosong")
    void nullStaysNull() {
        // Bedanya menentukan di jalur penyuntingan: null berarti "jangan
        // diubah", string kosong berarti "kosongkan". Menyamakan keduanya di
        // sini membuat permintaan yang tidak menyebut deskripsi justru
        // menghapusnya.
        assertThat(sanitizer.sanitize(null)).isNull();
    }

    @Test
    @DisplayName("isi yang seluruhnya berbahaya berakhir kosong, bukan setengah jadi")
    void fullyMaliciousContentEndsUpEmpty() {
        assertThat(sanitizer.sanitize("<script>alert(1)</script>")).isEmpty();
    }
}
