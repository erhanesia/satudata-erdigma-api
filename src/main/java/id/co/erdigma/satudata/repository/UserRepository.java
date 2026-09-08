package id.co.erdigma.satudata.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import id.co.erdigma.satudata.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByCognitoId(String cognitoId);

    /**
     * Daftar pengguna aktif untuk panel manajemen pengguna.
     *
     * `q` null berarti tanpa penyaringan. Pencocokan sebagian dan tanpa peduli
     * besar-kecil huruf, pada nama atau email — orang mencari rekannya dengan
     * potongan nama, bukan dengan ejaan persis.
     *
     * `CAST(:q AS string)` bukan hiasan: tanpanya, saat `q` null, Hibernate
     * tidak bisa menebak tipe JDBC parameter itu di dalam CONCAT, dan
     * PostgreSQL malah menyimpulkannya sebagai bytea — `LOWER(bytea)` gagal
     * dengan galat SQL. CAST memaksa parameter selalu bertipe teks.
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.deletedAt IS NULL
              AND (:q IS NULL
                   OR LOWER(u.name) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
            """)
    Page<User> cariAktif(@Param("q") String q, Pageable pageable);
}
