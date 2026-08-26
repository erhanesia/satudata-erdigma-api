package id.co.erdigma.satudata.modules.user.port;

import java.util.Optional;

import id.co.erdigma.satudata.entity.User;

/**
 * SEAM — satu-satunya titik di aplikasi ini yang tahu dari mana data karyawan
 * berasal. Implementasinya memanggil hris-api lewat HTTP lalu menyalin
 * hasilnya ke tabel {@code users} lokal sebagai bayangan.
 *
 * Sisa aplikasi TIDAK boleh bergantung pada implementasi mana pun — cukup
 * pakai {@code @CurrentUser User user} di controller.
 */
public interface EmployeeDirectory {

    Optional<User> findByCognitoId(String cognitoId);

    /**
     * Varian yang membawa token mentah si pemanggil, untuk bertanya ke HRIS
     * atas nama orang itu.
     */
    Optional<User> findByCognitoId(String cognitoId, String accessToken);
}
