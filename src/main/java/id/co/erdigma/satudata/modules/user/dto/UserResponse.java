package id.co.erdigma.satudata.modules.user.dto;

import id.co.erdigma.satudata.annotation.PrefixedId;
import id.co.erdigma.satudata.enums.IdPrefix;
import java.util.UUID;

import id.co.erdigma.satudata.enums.HrisPermissionLevel;
import id.co.erdigma.satudata.enums.Role;
import id.co.erdigma.satudata.modules.division.dto.DivisionResponseLite;

import lombok.Data;

/**
 * Identitas pengguna yang sedang masuk — dipakai header di setiap halaman
 * (nama, jabatan, inisial avatar).
 *
 * Tidak pernah memuat cognitoId. Nilai itu identitas internal untuk audit,
 * bukan sesuatu yang perlu diketahui browser.
 */
@Data
public class UserResponse {
    @PrefixedId(IdPrefix.USER)
    private UUID id;
    private String name;
    private String email;
    private String position;
    private String initials;
    private Role role;
    private HrisPermissionLevel hrisPermissionLevel;
    private String jobLevel;

    /**
     * URL utuh foto profil, siap dipasang di {@code <img src>}. Null kalau
     * karyawannya belum pernah mengunggah foto.
     *
     * Yang tersimpan di database cuma path-nya; URL ini disusun back-end dari
     * satu nilai konfigurasi. Sengaja BUKAN front-end yang menyusunnya: nama
     * bucket, region, dan tahap adalah pengetahuan infrastruktur, dan menaruhnya
     * di aplikasi web berarti setiap kali bucket-nya pindah, front-end ikut
     * harus dirilis ulang.
     */
    private String profileImageUrl;

    /*
     * `accessPosition` dicabut di changeset 48. Ruas itu menampung sembilan
     * label karangan yang kini digantikan `jobLevel` di atas, dan tidak pernah
     * terisi untuk satu pun pengguna Cognito.
     *
     * Antarmuka tetap bisa menjelaskan SEBAB sebuah dataset tidak muncul —
     * sekarang dari `jobLevel`, yang justru terisi sungguhan dari HRIS.
     */

    private DivisionResponseLite division;
}
