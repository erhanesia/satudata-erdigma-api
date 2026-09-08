package id.co.erdigma.satudata.modules.dataset.helper;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

/**
 * Membersihkan HTML yang datang dari editor teks kaya sebelum disimpan.
 *
 * <h2>Kenapa di sini, bukan di peramban</h2>
 *
 * Panel admin memang membersihkan isinya juga, dan itu berguna. Tetapi apa pun
 * yang dikerjakan peramban bisa dilewati begitu seseorang memanggil endpoint-nya
 * langsung dengan curl, dan yang tersimpan di database itulah yang kelak
 * digambar di halaman SEMUA orang yang berhak membuka dataset itu.
 *
 * Karena itu pembersihan di sini bukan lapisan tambahan melainkan lapisan
 * utamanya. Yang di klien hanya membuat kejanggalan terlihat lebih awal.
 *
 * <h2>Daftar putih, bukan daftar larangan</h2>
 *
 * Yang ditulis di bawah adalah apa yang BOLEH. Segala yang tidak disebut ikut
 * terbuang, termasuk yang belum ada saat kode ini ditulis.
 *
 * Kebalikannya — mendaftar apa yang dilarang — selalu kalah cepat. Setiap
 * peramban baru membawa elemen dan atribut baru, dan daftar larangan yang
 * ketinggalan tidak menimbulkan galat apa pun: ia cuma meloloskan sesuatu yang
 * tidak pernah dipertimbangkan siapa pun.
 *
 * <h2>Yang sengaja TIDAK diizinkan</h2>
 *
 * <b>Gambar dan berkas.</b> Editornya memang tidak menawarkannya, tetapi
 * larangan di sini yang membuatnya benar-benar tidak bisa masuk. Gambar dalam
 * editor semacam ini biasanya berakhir sebagai data URL yang membengkakkan satu
 * baris database sampai puluhan megabita, dan gambar dari alamat luar
 * membocorkan siapa yang membuka dataset ini ke server orang lain.
 *
 * <b>Atribut {@code style} dan {@code class}.</b> CSS bisa menutupi halaman,
 * memindahkan elemen ke tempat yang tidak semestinya, atau memuat alamat luar.
 * Rupa tulisannya datang dari lembar gaya aplikasi, bukan dari isi yang
 * diketik orang.
 *
 * <b>{@code <table>}.</b> Deskripsi dataset adalah keterangan tentang datanya,
 * bukan datanya sendiri. Tabel yang sesungguhnya sudah ada di Data Explorer.
 *
 * <h2>Tautan</h2>
 *
 * Hanya {@code http}, {@code https}, dan {@code mailto}. Yang paling penting
 * ditutup adalah {@code javascript:}, yang menjalankan kode begitu tautannya
 * ditekan. {@code rel="nofollow noopener noreferrer"} dipasang paksa: tanpa
 * {@code noopener}, halaman tujuan bisa mengarahkan ulang tab asalnya.
 */
@Component
public class RichTextSanitizer {

    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            // Penekanan kata. `b`/`i` ikut karena sebagian editor masih
            // menghasilkannya, dan membuangnya berarti kehilangan penekanan
            // yang sengaja ditulis orang.
            .allowElements("p", "br", "strong", "em", "b", "i", "u", "s")
            .allowElements("ul", "ol", "li")
            .allowElements("blockquote")
            /*
              SATU tingkat judul.

              Deskripsi dataset digambar di dalam kartu yang judulnya sendiri
              sudah menempati tingkat teratas, dan celah yang tersisa di
              bawahnya cuma cukup untuk satu tingkat yang benar-benar terbaca
              sebagai judul.

              Editornya memang hanya menawarkan satu tombol, tetapi larangan
              di SINI yang membuatnya benar-benar berlaku: tempelan dari Word
              dan permintaan yang dikirim langsung ke API tidak melewati
              editor itu. Tingkat lain tidak ditolak mentah-mentah melainkan
              kehilangan tag-nya — tulisannya tetap tinggal sebagai teks
              biasa, karena yang salah bentuknya, bukan isinya.
            */
            .allowElements("h2")
            .allowElements("a")
            .allowAttributes("href").onElements("a")
            .allowUrlProtocols("http", "https", "mailto")
            .requireRelNofollowOnLinks()
            .requireRelsOnLinks("noopener", "noreferrer")
            .toFactory();

    /**
     * Mengembalikan versi aman dari HTML yang dikirim, atau null kalau isinya
     * kosong.
     *
     * <h2>Kosong setelah dibersihkan berarti kosong</h2>
     *
     * Editor teks kaya menyimpan paragraf kosong sebagai {@code <p><br></p>}
     * ketika orang menghapus seluruh tulisannya. Dibiarkan apa adanya, dataset
     * yang deskripsinya sudah dikosongkan tetap terhitung "punya deskripsi",
     * sehingga kalimat "Belum ada deskripsi" tidak pernah muncul dan yang
     * tampil adalah kotak kosong tanpa penjelasan.
     */
    public String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = POLICY.sanitize(raw);
        return isBlankHtml(cleaned) ? "" : cleaned;
    }

    /**
     * Apakah HTML ini tidak memuat satu pun teks yang bisa dibaca.
     *
     * <h2>Kenapa spasi tanpa-pemisah diurus khusus</h2>
     *
     * Editor menyisipkan {@code &nbsp;} untuk menahan paragraf kosong, dan
     * pembersihnya menguraikan entitas itu menjadi karakternya sendiri,
     * U+00A0. Java TIDAK menganggapnya spasi: {@code trim()},
     * {@code isBlank()}, maupun {@code \s} pada regex sama-sama
     * melewatkannya.
     *
     * Akibatnya kalau tidak diurus: paragraf yang isinya cuma satu spasi tak
     * terlihat dianggap sebagai deskripsi yang ada, dan dataset yang
     * deskripsinya sudah dikosongkan menampilkan kotak kosong alih-alih
     * kalimat "Belum ada deskripsi".
     *
     * Bentuk entitasnya ikut diganti untuk berjaga kalau suatu saat
     * pembersihnya berhenti menguraikannya.
     */
    private boolean isBlankHtml(String html) {
        String text = html.replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ")
                .replace(' ', ' ')
                .trim();
        return text.isEmpty();
    }
}
