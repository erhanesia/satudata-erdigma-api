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
     *
     * `LEFT JOIN FETCH u.division` mencegah N+1: `division` bertipe LAZY dan
     * dibaca untuk setiap baris di {@code UserAdminService.toResponse}, jadi
     * tanpa fetch join satu halaman berisi 20 baris akan memicu 20 SELECT
     * tambahan ke tabel division. LEFT, bukan INNER, supaya baris tanpa
     * divisi tidak ikut hilang dari hasil. `countQuery` ditulis terpisah
     * tanpa join itu — menghitung jumlah baris tidak butuh data division,
     * dan Spring Data tidak selalu bisa menurunkan count query yang benar
     * dari query yang sudah memakai fetch join.
     */
    @Query(value = """
            SELECT u FROM User u
            LEFT JOIN FETCH u.division
            WHERE u.deletedAt IS NULL
              AND (:q IS NULL
                   OR LOWER(u.name) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
            """, countQuery = """
            SELECT COUNT(u) FROM User u
            WHERE u.deletedAt IS NULL
              AND (:q IS NULL
                   OR LOWER(u.name) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
            """)
    Page<User> cariAktif(@Param("q") String q, Pageable pageable);
}
