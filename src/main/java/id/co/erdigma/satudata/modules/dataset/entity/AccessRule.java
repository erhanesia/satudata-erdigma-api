package id.co.erdigma.satudata.modules.dataset.entity;

import id.co.erdigma.satudata.enums.AccessRuleType;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Satu baris aturan "siapa boleh melihat" pada sebuah dataset.
 *
 * Embeddable, bukan entitas tersendiri. Aturan tidak punya jati diri di luar
 * dataset yang memilikinya: tidak ada yang perlu merujuk sebuah aturan, dan
 * menghapus datasetnya berarti aturannya ikut hilang tanpa sisa. Memberinya id
 * sendiri hanya menambah kolom yang tidak pernah dibaca siapa pun.
 *
 * {@code equals} dan {@code hashCode} dari Lombok penting di sini. Hibernate
 * memakai keduanya untuk memutuskan baris mana yang perlu ditambah atau dihapus
 * saat koleksinya berubah; tanpa itu, setiap penyimpanan akan menghapus seluruh
 * baris lalu menulisnya kembali.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class AccessRule {

    /**
     * Disimpan sebagai teks, bukan angka urutan enum.
     *
     * ORDINAL akan menautkan makna pada posisi deklarasi, sehingga menyisipkan
     * satu nilai baru di tengah enum diam-diam mengubah arti seluruh baris yang
     * sudah tersimpan. Untuk tabel yang menentukan siapa boleh melihat apa,
     * kesalahan seperti itu tidak akan terlihat sampai ada yang membuka dataset
     * yang seharusnya tertutup.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 20)
    private AccessRuleType ruleType;

    /**
     * Isinya bergantung {@link #ruleType}: LABEL jenjang jabatan HRIS
     * (mis. {@code Senior Manager}, bukan {@code SENIOR_MANAGER}), UUID posisi,
     * atau UUID karyawan.
     *
     * Bentuk pastinya beserta alasannya ada di {@link AccessRuleType}, dan
     * sengaja tidak diulang panjang lebar di sini. Fakta yang sama sempat
     * tertulis di lima tempat berbeda, dan yang di sini menyimpang jadi "nama
     * enum" tanpa ada yang menyadarinya — karena tidak ada yang gagal kalau
     * sebuah kalimat berbohong.
     */
    @Column(name = "rule_value", nullable = false, length = 60)
    private String ruleValue;
}
