package id.co.erdigma.satudata.service.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mengunci janji bahwa menyimpan CSV terkompresi TIDAK mengubah isinya.
 *
 * <h2>Kenapa kelas ini ada</h2>
 *
 * Ini katalog data. Berkas yang berubah satu karakter pun karena disimpan lebih
 * hemat adalah kerusakan yang jauh lebih mahal daripada ruang yang dihematnya,
 * dan bentuk kerusakannya senyap: CSV yang kehilangan baris terakhirnya tetap
 * terbuka normal di Excel, dan yang membacanya menyimpulkan datanya memang
 * segitu.
 *
 * Jaminannya datang dari cara membangunnya, bukan dari sifat bawaan kompresi.
 * Kalau suatu saat ada yang mengganti gzip dengan sesuatu yang lossy, atau
 * membuat {@code open} lupa membuka kompresinya, tes ini yang gagal lebih dulu.
 */
class GzipStorageTest {

    /**
     * Isi yang menyerupai ekspor log sungguhan: sangat berulang, karena itulah
     * yang membuat CSV menyusut sampai 7,2% pada berkas nyata di proyek ini.
     */
    private static String csvBerulang(int baris) {
        StringBuilder sb = new StringBuilder(
                "waktu,jenis_akses,nama,email,divisi,dataset,berkas,format\n");
        for (int i = 0; i < baris; i++) {
            sb.append("2026-09-10T08:15:00,DOWNLOAD,Budi Santoso,budi@erdigma.co.id,")
                    .append("DIT,laporan-penjualan,laporan-penjualan.csv,CSV\n");
        }
        return sb.toString();
    }

    /*
      Inti kelas ini.

      Yang diperiksa bukan "kira-kira sama", melainkan setiap byte. Perbandingan
      yang longgar akan meloloskan justru kerusakan yang paling berbahaya:
      beberapa baris terakhir yang hilang.
    */
    @Test
    @DisplayName("isi CSV kembali utuh byte per byte setelah bolak-balik kompresi")
    void roundTripIsLossless(@TempDir Path dir) throws IOException {
        byte[] asli = csvBerulang(5_000).getBytes(StandardCharsets.UTF_8);
        Path sumber = dir.resolve("log.csv");
        Files.write(sumber, asli);

        Path terkompresi = GzipStorage.compressToTemp(sumber);

        byte[] kembali;
        try (InputStream in = GzipStorage.decompressIfNeeded(
                "dataset/x/log.csv" + GzipStorage.SUFFIX, Files.newInputStream(terkompresi))) {
            kembali = in.readAllBytes();
        } finally {
            Files.deleteIfExists(terkompresi);
        }

        assertThat(kembali).isEqualTo(asli);
    }

    @Test
    @DisplayName("CSV yang berulang menyusut jauh")
    void repetitiveCsvShrinks(@TempDir Path dir) throws IOException {
        Path sumber = dir.resolve("log.csv");
        Files.write(sumber, csvBerulang(5_000).getBytes(StandardCharsets.UTF_8));

        Path terkompresi = GzipStorage.compressToTemp(sumber);
        try {
            // Ambangnya longgar dengan sengaja. Yang dijaga di sini "kompresinya
            // benar-benar bekerja", bukan angka tertentu yang akan berubah
            // mengikuti versi zlib.
            assertThat(Files.size(terkompresi)).isLessThan(Files.size(sumber) / 4);
        } finally {
            Files.deleteIfExists(terkompresi);
        }
    }

    /*
      Berkas yang tersimpan sebelum fitur ini ada TIDAK boleh ikut dibuka
      kompresinya.

      Kuncinya tidak berakhiran .gz, dan memaksakan GZIPInputStream padanya akan
      melempar galat pada setiap unduhan berkas lama. Keadaan campur ini
      permanen: tidak ada rencana mengisi ulang berkas yang sudah ada.
    */
    @Test
    @DisplayName("berkas tanpa akhiran .gz diteruskan apa adanya")
    void plainKeyIsPassedThrough() throws IOException {
        byte[] isi = "nama,umur\nBudi,30\n".getBytes(StandardCharsets.UTF_8);

        try (InputStream in = GzipStorage.decompressIfNeeded(
                "dataset/x/lama.csv", new ByteArrayInputStream(isi))) {
            assertThat(in.readAllBytes()).isEqualTo(isi);
        }
    }

    /*
      Hanya CSV, dan hanya yang cukup besar.

      PDF, DOCX, dan XLSX sudah mengompresi dirinya sendiri; membungkusnya lagi
      menambah ukuran alih-alih mengurangi. Diukur pada berkas nyata: sebuah ZIP
      berisi data yang sama justru bertambah 1 KB setelah di-gzip.
    */
    @Test
    @DisplayName("hanya CSV berukuran cukup yang dipilih untuk dikompresi")
    void onlyLargeCsvIsChosen() {
        long besar = 5L * 1024 * 1024;

        assertThat(GzipStorage.worthCompressing("dataset/x/data.csv", besar)).isTrue();
        assertThat(GzipStorage.worthCompressing("dataset/x/DATA.CSV", besar)).isTrue();

        assertThat(GzipStorage.worthCompressing("dataset/x/laporan.pdf", besar)).isFalse();
        assertThat(GzipStorage.worthCompressing("dataset/x/rekap.xlsx", besar)).isFalse();
        assertThat(GzipStorage.worthCompressing("dataset/x/surat.docx", besar)).isFalse();

        // Berkas kecil dibiarkan: yang dihemat sedikit, sedangkan ongkos membuka
        // kompresinya dibayar pada setiap pembacaan selamanya.
        assertThat(GzipStorage.worthCompressing("dataset/x/data.csv", 1024)).isFalse();
    }

    /*
      Berkas yang dikirim peramban dalam keadaan ter-gzip dikenali dari ISINYA.

      Namanya tetap berakhiran .csv, karena dari situlah jenis berkasnya dibaca;
      diberi akhiran .gz, ia akan tertolak sebagai jenis yang tidak didukung.
      Tipe MIME juga tidak bisa dipercaya karena datang dari peramban.
    */
    @Test
    @DisplayName("berkas ter-gzip yang tiba dikenali, dibuka, dan aslinya tetap disimpan")
    void gzippedUploadIsRecognised(@TempDir Path dir) throws IOException {
        byte[] asli = csvBerulang(2_000).getBytes(StandardCharsets.UTF_8);
        Path sumber = dir.resolve("data.csv");
        Files.write(sumber, asli);

        // Meniru yang dikirim peramban: byte gzip, tetapi bernama .csv.
        Path terkirim = GzipStorage.compressToTemp(sumber);

        GzipStorage.Terkirim datang = GzipStorage.receive(terkirim);
        try {
            assertThat(Files.readAllBytes(datang.isi())).isEqualTo(asli);

            // Byte yang dikirim TIDAK dibuang: itulah yang akan disimpan, supaya
            // kompresinya tidak dikerjakan dua kali.
            assertThat(datang.terkompresi()).isEqualTo(terkirim);
            assertThat(Files.exists(terkirim)).isTrue();
            assertThat(Files.size(terkirim)).isLessThan(asli.length);
        } finally {
            Files.deleteIfExists(terkirim);
            Files.deleteIfExists(datang.isi());
        }
    }

    @Test
    @DisplayName("berkas biasa diteruskan apa adanya, tanpa salinan tambahan")
    void plainUploadIsUntouched(@TempDir Path dir) throws IOException {
        Path sumber = dir.resolve("data.csv");
        Files.write(sumber, ("nama,umur\nBudi,30\n").getBytes(StandardCharsets.UTF_8));

        GzipStorage.Terkirim datang = GzipStorage.receive(sumber);

        assertThat(datang.isi()).isEqualTo(sumber);
        assertThat(datang.terkompresi()).isNull();
    }

    /*
      Sidik jari harus milik isi ASLI.

      Kalau dihitung dari byte gzip, ia tidak akan pernah cocok dengan berkas
      yang ada di tangan orang yang mengunduhnya, dan pemeriksaan keutuhan
      berkas justru melaporkan kerusakan pada berkas yang baik-baik saja.
    */
    @Test
    @DisplayName("sidik jari dihitung dari isi asli, bukan dari byte terkompresi")
    void checksumIsOfTheOriginal(@TempDir Path dir) throws IOException {
        Path sumber = dir.resolve("data.csv");
        Files.write(sumber, csvBerulang(500).getBytes(StandardCharsets.UTF_8));

        Path terkompresi = GzipStorage.compressToTemp(sumber);
        try {
            assertThat(GzipStorage.sha256(sumber))
                    .isNotEqualTo(GzipStorage.sha256(terkompresi));

            // Dan ia harus cocok dengan isi yang kelak diterima pengunduh.
            Path pulang = dir.resolve("pulang.csv");
            try (InputStream in = GzipStorage.decompressIfNeeded(
                    "x.csv" + GzipStorage.SUFFIX, Files.newInputStream(terkompresi))) {
                Files.write(pulang, in.readAllBytes());
            }
            assertThat(GzipStorage.sha256(pulang)).isEqualTo(GzipStorage.sha256(sumber));
        } finally {
            Files.deleteIfExists(terkompresi);
        }
    }
}
