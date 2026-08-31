package id.co.erdigma.satudata.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import id.co.erdigma.satudata.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByCognitoId(String cognitoId);

    /**
     * Isi kartu "Pengguna aktif" di dasbor admin.
     *
     * "Aktif" di sini berarti barisnya belum di-soft-delete, BUKAN "pernah
     * masuk belakangan ini" — portal ini stateless dan tidak menyimpan waktu
     * kunjungan terakhir siapa pun. Begitu HRIS jadi sumber datanya, angka ini
     * mengikuti status kepegawaian di sana.
     */
    long countByDeletedAtIsNull();
}
