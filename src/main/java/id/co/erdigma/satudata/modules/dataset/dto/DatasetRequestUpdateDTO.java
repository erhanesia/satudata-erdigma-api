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
 * dataset antar divisi mengubah siapa yang bertanggung jawab atasnya — keputusan
 * yang lebih besar daripada merapikan keterangan, dan tidak semestinya tersedia
 * di layar yang sama.
 *
 * <b>Berkas.</b> Mengganti isi dataset berbeda sifatnya dari mengganti
 * keterangannya, dan sudah punya jalurnya sendiri di {@code POST /{slug}/reimport}.
 * Menggabungkan keduanya membuat satu layar memegang dua tindakan dengan risiko
 * yang jauh berbeda.
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

    @Schema(description = "Deskripsi dataset. Kirim string kosong untuk mengosongkannya; "
            + "hilangkan ruasnya kalau tidak ingin mengubahnya.")
    private String notes;

    @Schema(description = "Peringatan yang tampil sebelum unduhan. Kirim string kosong untuk "
            + "mengosongkannya; hilangkan ruasnya kalau tidak ingin mengubahnya.")
    private String disclaimer;

    @Schema(description = "Periode yang dicakup data.", example = "Jan – Des 2025")
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
}
