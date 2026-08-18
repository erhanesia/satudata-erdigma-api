package id.co.erdigma.satudata.modules.user.port;

import java.util.Optional;

import id.co.erdigma.satudata.entity.User;

/**
 * SEAM — satu-satunya titik di aplikasi ini yang tahu dari mana data karyawan
 * berasal. Selama profil {@code auth-dummy} implementasinya membaca tabel
 * {@code users} lokal; saat integrasi, implementasi HRIS memanggil API-nya
 * lewat HTTP lalu menyalin hasilnya ke tabel yang sama.
 *
 * Sisa aplikasi TIDAK boleh bergantung pada implementasi mana pun — cukup
 * pakai {@code @CurrentUser User user} di controller.
 */
public interface EmployeeDirectory {

    Optional<User> findByCognitoId(String cognitoId);
}
