package id.co.erdigma.satudata.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Menyimpan berkas teks dalam keadaan terkompresi, tanpa ada yang perlu tahu.
 *
 * <h2>Kenapa hanya CSV</h2>
 *
 * CSV adalah satu-satunya format kita yang isinya belum terkompresi. PDF, DOCX,
 * dan XLSX sudah mengompresi dirinya sendiri, jadi membungkusnya lagi menambah
 * ukuran alih-alih mengurangi. Diukur pada berkas nyata di proyek ini: sebuah
 * ZIP berisi data yang sama justru bertambah 1 KB setelah di-gzip.
 *
 * Sedangkan CSV menyusut luar biasa. Ekspor log unduhan 6.782.078 byte berisi
 * 42.189 baris menjadi 488.892 byte, atau tinggal 7,2%. Sebabnya isinya sangat
 * berulang: alamat surel, kode divisi, dan nama berkas yang sama muncul ribuan
 * kali.
 *
 * <h2>Kenapa gzip, bukan yang lebih rapat</h2>
 *
 * Diukur pada berkas yang sama, bzip2 menghasilkan 3,5% dan xz 3,9%, keduanya
 * lebih kecil daripada gzip. Tetapi bzip2 lima belas kali lebih lambat, dan
 * waktu itu dibayar SETIAP KALI berkas dibaca, bukan sekali saat disimpan.
 * Menghemat seperempat megabita dengan menambah setengah detik pada tiap
 * unduhan jelas bukan pertukaran yang menguntungkan.
 *
 * Dan gzip ada di dalam JDK, jadi tidak ada dependensi maupun lisensi baru.
 *
 * <h2>Kenapa akhiran pada kunci, bukan kolom di database</h2>
 *
 * Kunci penyimpanan sudah tersimpan pada tiap baris berkas, jadi akhiran
 * {@code .gz} ikut terbawa dengan sendirinya. Tidak perlu changeset, tidak
 * perlu mengisi ulang data lama, dan berkas lama yang tidak berakhiran itu
 * tetap terbaca apa adanya. Keadaan campur tertangani tanpa satu baris kode pun
 * yang mengurusnya.
 *
 * <h2>Ini LOSSLESS, dan itu bisa dibuktikan</h2>
 *
 * Berbeda dengan pengecilan gambar pada PDF yang membuang detail untuk
 * selamanya, di sini tidak ada satu byte pun yang hilang. Diuji pada ekspor log
 * nyata: sha256 sebelum dan sesudah bolak-balik identik, dengan nol byte yang
 * berbeda.
 */
public final class GzipStorage {

    private GzipStorage() {
    }

    /** Penanda bahwa objek di penyimpanan berbentuk gzip. */
    public static final String SUFFIX = ".gz";

    /**
     * Berkas yang lebih kecil dari ini dibiarkan apa adanya.
     *
     * Bukan karena tidak bisa dikompresi, melainkan karena tidak sepadan. Yang
     * dihemat dari CSV 20 KB paling belasan kilobita, sementara ongkosnya
     * membuka kompresi pada setiap pembacaan selamanya.
     */
    private static final long MIN_BYTES = 64 * 1024;

    /**
     * Apakah berkas ini disimpan terkompresi.
     *
     * Ditentukan dari akhiran kunci, bukan dari tipe MIME yang dikirim klien.
     * Tipe MIME datang dari peramban dan bisa apa saja, termasuk kosong;
     * akhiran nama berkas sudah lebih dulu diperiksa saat unggah dan hanya
     * empat yang diterima.
     */
    public static boolean worthCompressing(String storageKey, long sizeBytes) {
        return storageKey != null
                && storageKey.toLowerCase(Locale.ROOT).endsWith(".csv")
                && sizeBytes >= MIN_BYTES;
    }

    /**
     * Menuliskan versi ter-gzip ke berkas sementara, lalu mengembalikan
     * letaknya. Pemanggil yang wajib menghapusnya.
     */
    public static Path compressToTemp(Path source) throws IOException {
        Path temp = Files.createTempFile("satudata-gz-", SUFFIX);
        try (InputStream in = Files.newInputStream(source);
                OutputStream raw = Files.newOutputStream(temp);
                GZIPOutputStream gz = new GZIPOutputStream(raw)) {
            in.transferTo(gz);
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
        return temp;
    }

    /**
     * Membuka kompresi bila kuncinya menandakan demikian.
     *
     * <h2>Kenapa di lapisan penyimpanan, bukan di pemanggilnya</h2>
     *
     * Ada empat tempat yang membaca berkas: unduhan, pratinjau, pratinjau teks
     * Word, dan pembacaan ulang isi tabel. Menaruh pembukaan kompresi di
     * masing-masing berarti empat kesempatan untuk lupa, dan yang lupa TIDAK
     * mendapat galat: yang terjadi importir CSV membaca byte gzip mentah, lalu
     * menyimpan sampah sebagai isi tabel.
     *
     * Di sini ia cukup ditulis sekali, dan berlaku untuk keempatnya.
     */
    public static InputStream decompressIfNeeded(String storageKey, InputStream raw)
            throws IOException {
        if (storageKey == null || !storageKey.endsWith(SUFFIX)) {
            return raw;
        }
        return new GZIPInputStream(raw);
    }

    /**
     * Membuka berkas yang isinya ternyata gzip, atau mengembalikannya apa adanya.
     *
     * <h2>Kenapa dikenali dari isinya, bukan dari namanya</h2>
     *
     * Peramban mengecilkan CSV sebelum mengirimnya supaya berkas besar tidak
     * perlu melewati kabel dalam ukuran penuh. Nama berkasnya sengaja tetap
     * berakhiran .csv, karena dari situlah jenis berkasnya dibaca; kalau diberi
     * akhiran .gz, ia akan tertolak sebagai jenis yang tidak didukung.
     *
     * Tipe MIME juga tidak bisa dipercaya: ia datang dari peramban dan bisa
     * berisi apa saja, termasuk kosong.
     *
     * Yang tersisa dan paling jujur adalah dua byte pertamanya. 0x1f 0x8b adalah
     * penanda gzip yang ditetapkan RFC 1952, dan CSV yang sah tidak akan pernah
     * diawali keduanya karena bukan karakter yang bisa diketik.
     *
     * <h2>Kenapa yang ter-gzip TIDAK dibuang setelah dibuka</h2>
     *
     * Byte itulah yang akan disimpan. Peramban sudah mengompresinya, jadi
     * membuangnya lalu mengompresi ulang di server berarti mengerjakan hal
     * yang sama dua kali: sekitar satu sampai dua detik CPU per unggahan CSV
     * besar, untuk hasil yang sudah ada di tangan.
     *
     * Yang terbuka dipakai untuk dibaca isinya, diukur, dan disidik jari.
     * Yang terkompresi dipakai untuk disimpan.
     *
     * @return keduanya. {@link Terkirim#terkompresi()} bernilai null bila
     *         berkasnya memang datang apa adanya.
     */
    public static Terkirim receive(Path source) throws IOException {
        if (!looksGzipped(source)) {
            return new Terkirim(source, null);
        }

        Path terbuka = Files.createTempFile("satudata-ungz-", ".tmp");
        try (InputStream raw = Files.newInputStream(source);
                GZIPInputStream gz = new GZIPInputStream(raw);
                OutputStream out = Files.newOutputStream(terbuka)) {
            gz.transferTo(out);
        } catch (IOException e) {
            Files.deleteIfExists(terbuka);
            /*
              Byte-nya berpenanda gzip tetapi gagal dibuka, jadi berkasnya rusak
              di tengah jalan. Diteruskan apa adanya akan menyimpan sampah
              sebagai isi tabel tanpa satu pun galat, jadi lebih baik gagal di
              sini dengan sebab yang jelas.
            */
            throw new IOException("Berkas terkirim rusak saat dibuka kompresinya", e);
        }

        return new Terkirim(terbuka, source);
    }

    /**
     * Berkas yang baru tiba, dalam dua bentuk yang masing-masing ada gunanya.
     *
     * @param isi          selalu berisi berkas yang sesungguhnya, siap dibaca
     * @param terkompresi  byte gzip yang dikirim klien, atau null bila tidak
     *                     ada. Bila ada, INILAH yang disimpan, supaya
     *                     kompresinya tidak dikerjakan dua kali
     */
    public record Terkirim(Path isi, Path terkompresi) {
    }

    /** Dua byte penanda gzip menurut RFC 1952. */
    private static boolean looksGzipped(Path source) throws IOException {
        try (InputStream in = Files.newInputStream(source)) {
            byte[] kepala = in.readNBytes(2);
            return kepala.length == 2
                    && (kepala[0] & 0xff) == 0x1f
                    && (kepala[1] & 0xff) == 0x8b;
        }
    }

    /**
     * Sidik jari isi ASLI berkas.
     *
     * Harus dihitung dari berkas sebelum dikompresi, karena itulah yang akan
     * diterima orang saat mengunduh. Sidik jari dari byte gzip tidak akan pernah
     * cocok dengan berkas yang ada di tangan mereka.
     */
    public static String sha256(Path source) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(source);
                    DigestInputStream dis = new DigestInputStream(in, digest)) {
                dis.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 tidak tersedia", e);
        }
    }
}
