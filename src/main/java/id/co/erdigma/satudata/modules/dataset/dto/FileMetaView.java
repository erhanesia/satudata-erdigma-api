package id.co.erdigma.satudata.modules.dataset.dto;

/**
 * Keterangan satu berkas, sejauh yang dibutuhkan pemeriksa unggahan.
 *
 * <h2>Kenapa antarmuka, bukan satu DTO yang dipakai bersama</h2>
 *
 * Formulir terbit dan formulir sunting meminta hal yang berbeda. Yang terbit
 * hanya perlu nama dan jenis; yang sunting juga perlu {@code id} untuk menunjuk
 * berkas yang sudah ada. Menyatukannya jadi satu DTO berarti salah satu sisi
 * memegang ruas yang tidak pernah ia pakai, dan ruas semacam itu selalu
 * berakhir terisi oleh seseorang yang mengira ia berguna.
 *
 * Yang benar-benar dipakai bersama justru <b>pemeriksaannya</b>: batas ukuran,
 * pencocokan jenis dengan ekstensi, dan pemasangan menurut urutan. Pemeriksaan
 * itu urusan keamanan, dan menyalinnya ke dua tempat berarti suatu saat hanya
 * satu yang diperbaiki. Antarmuka ini adalah bagian tersempit yang perlu
 * dilihat pemeriksa itu, jadi kedua DTO bisa melewatinya tanpa saling meniru.
 *
 * Lombok {@code @Data} pada kedua DTO sudah menghasilkan kedua getter ini, jadi
 * keduanya cukup menyatakan {@code implements} tanpa menulis kode tambahan.
 */
public interface FileMetaView {

    /** Nama berkas versi manusia. Boleh kosong; pemanggil menyediakan gantinya. */
    String getLabel();

    /**
     * Jenis berkas yang dinyatakan pengirim, misalnya {@code CSV}.
     *
     * Boleh kosong, dan kalau kosong jenisnya dibaca dari ekstensi. Kalau diisi,
     * ia diperiksa terhadap berkas yang sungguh dikirim dan penolakannya tegas:
     * lencana "PDF" pada berkas yang isinya CSV adalah keterangan salah, dan
     * keterangan salah di katalog data lebih berbahaya daripada penolakan.
     */
    String getFormat();
}
