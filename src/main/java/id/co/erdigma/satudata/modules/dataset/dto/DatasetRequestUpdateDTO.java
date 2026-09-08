package id.co.erdigma.satudata.modules.dataset.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * Isi permintaan untuk menyunting dataset yang sudah terbit.
 *
 * <h2>Apa yang TIDAK ada di sini, dan kenapa</h2>
 *
 * <b>Slug.</b> Judul boleh dirapikan berkali-kali; alamatnya tidak boleh ikut
 * goyah. Slug dipakai orang untuk membagikan tautan di grup, email, dan catatan
 * rapat, dan mengubahnya berarti mematikan setiap tautan yang sudah beredar.
 * Kalau suatu saat perlu diubah, itu tindakan tersendiri yang harus dinyatakan
 * sadar, bukan efek samping dari menyunting judul.
 *
 * <b>Divisi.</b> Penanda kepemilikan, dicatat saat penerbitan. Memindahkan
 * dataset antar divisi mengubah siapa yang bertanggung jawab atasnya, keputusan
 * yang lebih besar daripada merapikan keterangan, dan tidak semestinya tersedia
 * di layar yang sama.
 *
 * <b>Pengunggah.</b> Jejak siapa yang menerbitkan, bukan penanda pemilik saat
 * ini. Penyunting berikutnya tidak menggantikannya; yang mencatat siapa
 * menyunting apa adalah jejak audit.
 *
 * <h2>Ruas kosong berarti apa</h2>
 *
 * Untuk ruas keterangan, {@code null} berarti <b>jangan diubah</b>, dan string
 * kosong berarti <b>kosongkan</b>. Bedanya penting: klien yang hanya ingin
 * mengganti judul tidak perlu ikut mengirim seluruh ruas lain, dan tidak akan
 * menghapus deskripsi yang tidak ia sentuh.
 *
 * {@code accessRules} dikecualikan dan WAJIB disertakan. Alasannya ada di ruas
 * itu sendiri.
 */
@Data
public class DatasetRequestUpdateDTO {

    @NotBlank(message = "Judul wajib diisi")
    @Size(max = 255, message = "Judul maksimal 255 karakter")
    @Schema(description = "Judul dataset. Slug TIDAK ikut berubah meski judulnya diganti, "
            + "supaya tautan yang sudah beredar tetap hidup.",
            example = "Penjualan Furnitur Ritel 2025", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "Deskripsi dataset, berupa **HTML terbatas** dari editor teks kaya. "
            + "Yang dipertahankan hanya p, br, strong, em, b, i, u, s, ul, ol, li, blockquote, "
            + "h2, dan a[href]; selebihnya DIBUANG saat disimpan, termasuk atribut style, "
            + "gambar, dan penangan kejadian. Teks polos tetap diterima apa adanya. Kirim string "
            + "kosong untuk mengosongkannya; hilangkan ruasnya kalau tidak ingin mengubahnya.")
    private String notes;

    @Schema(description = "Peringatan yang tampil sebelum unduhan. Kirim string kosong untuk "
            + "mengosongkannya; hilangkan ruasnya kalau tidak ingin mengubahnya.")
    private String disclaimer;

    @Schema(description = "Periode yang dicakup data.", example = "Jan - Des 2025")
    private String coverage;

    @Schema(description = "Nama topik, ambil dari GET /api/v1/topics. Menggantikan daftar lama, "
            + "bukan menambah. Hilangkan ruasnya kalau tidak ingin mengubahnya.",
            example = "[\"Penjualan\"]")
    private List<String> topics;

    @Schema(description = "Slug koleksi induk, ambil dari GET /api/v1/collections. Kirim string "
            + "kosong untuk melepas dataset dari koleksinya.", example = "komersial")
    private String collectionSlug;

    /*
      Wajib ADA, tetapi boleh kosong. Dua hal yang berbeda, dan bedanya
      menentukan.

      Kalau ruas ini boleh hilang seperti ruas keterangan di atas, badan
      permintaan yang lupa menyertakannya akan terbaca sebagai "jangan diubah" --
      dan itu memang aman. Tetapi begitu suatu saat ada yang menyamakan
      perlakuannya dengan daftar kosong, seluruh pembatasan dataset terhapus
      tanpa satu pun galat.

      Kejadian itu sudah pernah nyaris terjadi di endpoint access-rules, dan
      ditemukan review sebelum sempat tayang. Mewajibkan ruasnya membuat
      pilihannya selalu dinyatakan terang: daftar berisi untuk membatasi, daftar
      kosong untuk membuka.
    */
    @NotNull(message = "Daftar aturan akses wajib disertakan. Kirim daftar kosong "
            + "kalau memang ingin membukanya untuk seluruh karyawan.")
    @Schema(description = "Aturan siapa yang boleh melihat, MENGGANTIKAN yang lama. Ruas ini "
            + "WAJIB ADA. Kirim daftar kosong untuk membuka dataset ini bagi seluruh karyawan; "
            + "menghilangkan ruasnya ditolak dengan 400.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<@Valid AccessRuleDTO> accessRules;

    /*
      Berkas: dihilangkan berarti "jangan disentuh", dikirim berarti "beginilah
      seharusnya isinya".

      Bentuk keadaan-akhir dipilih karena itu yang benar-benar diketahui
      formulir. Layar sunting memuat daftar berkas yang ada, orang menambah,
      mengganti nama, dan mencoret beberapa, lalu menekan Simpan. Yang ia
      maksud adalah daftar yang sedang ia lihat, bukan rentetan perintah tambah
      dan hapus. Menerjemahkannya jadi rentetan perintah di sisi klien hanya
      menciptakan kemungkinan setengah jalan: berkas terhapus lalu penambahnya
      gagal, dan tidak ada transaksi yang bisa mengembalikannya.

      Sebaliknya, membiarkan ruas ini hilang tetap harus mungkin. Klien yang
      cuma ingin memperbaiki salah ketik pada judul tidak boleh dipaksa
      menyebutkan ulang seluruh berkasnya -- dan kalau ia lupa, kelupaan itu
      tidak boleh berarti "hapus semuanya".
    */
    @Schema(description = "Keadaan akhir daftar berkas. HILANGKAN ruas ini kalau tidak ingin "
            + "menyentuh berkas sama sekali. Kalau dikirim, berkas lama yang TIDAK disebut di "
            + "sini akan dihapus. Entri ber-`id` menunjuk berkas yang sudah ada; entri tanpa "
            + "`id` adalah berkas baru dan dipasangkan menurut urutan dengan bagian `files` "
            + "pada multipart.")
    private List<@Valid FileEdit> files;

    /**
     * Satu baris berkas pada formulir sunting.
     *
     * <h2>Ada atau tidaknya {@code id} yang membedakan segalanya</h2>
     *
     * Beri {@code id}, dan entri ini menunjuk berkas yang sudah tersimpan:
     * isinya tidak disentuh, hanya namanya yang bisa dirapikan. Kosongkan
     * {@code id}, dan entri ini berkas baru yang harus punya pasangan di bagian
     * {@code files} pada multipart.
     *
     * Pemasangan berkas baru BERDASARKAN URUTAN, sama seperti pada penerbitan:
     * entri tanpa {@code id} yang ke-n dipasangkan dengan bagian {@code files}
     * ke-n. Karena itu jumlah keduanya harus sama persis, dan itu diperiksa
     * lebih dulu sebelum apa pun disimpan.
     */
    @Data
    public static class FileEdit implements FileMetaView {

        /*
          Bertipe String, bukan UUID, dan itu bukan kelalaian.

          Seluruh id di API ini keluar dalam bentuk BERAWALAN -- `dres-` untuk
          berkas dataset -- lewat @PrefixedId pada DTO responsnya. Formulir
          sunting memuat daftar berkas dari respons itu lalu mengirimkan
          id-nya kembali apa adanya, jadi yang tiba di sini memang berawalan.

          Ruas bertipe UUID akan menolaknya di lapisan Jackson, sebelum satu
          baris kode aplikasi sempat berjalan, dan yang sampai ke pengguna
          cuma "Permintaan tidak valid." tanpa menyebut ruas mana yang salah.
          Sebagai String, awalannya dilepas IdPrefix.parse() yang juga
          menerima UUID telanjang dan menolak awalan milik tabel lain dengan
          pesan yang menyebut bentuk yang benar.
        */
        @Schema(description = "Id berkas yang sudah ada, dalam bentuk berawalan seperti "
                + "yang dikirim GET /api/v1/datasets/{slug}. Kosongkan untuk berkas baru.",
                example = "dres-e0000000-0000-4000-8000-000000000009")
        private String id;

        @Size(max = 255, message = "Nama file maksimal 255 karakter")
        @Schema(description = "Nama berkas versi manusia.", example = "Kamus Kolom")
        private String label;

        @Schema(description = "Jenis berkas baru: CSV, XLSX, PDF, atau DOCX. Harus cocok dengan "
                + "ekstensi berkas yang dikirim. Diabaikan untuk entri ber-`id`, karena jenis "
                + "berkas yang sudah tersimpan tidak bisa berubah tanpa mengganti isinya.",
                example = "CSV")
        private String format;
    }
}
