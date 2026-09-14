package id.co.erdigma.satudata.modules.audit.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import id.co.erdigma.satudata.modules.audit.entity.AuditLog;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByRecordedAtDesc(Pageable pageable);

    Page<AuditLog> findAllByObjectSlugOrderByRecordedAtDesc(String objectSlug, Pageable pageable);

    /**
     * Jejak audit yang boleh dilihat admin sebuah divisi.
     *
     * <h2>Dua jenis baris, dua aturan</h2>
     *
     * Baris bertipe {@code dataset} dicocokkan lewat divisi DATASET-nya,
     * sehingga admin melihat apa saja yang terjadi pada data divisinya,
     * termasuk bila yang melakukannya admin HRIS dari luar divisi itu.
     * Mencocokkannya lewat divisi pelaku justru menyembunyikan hal itu,
     * padahal justru itu yang paling perlu diketahui pemilik datanya.
     *
     * Baris jenis lain, sejauh ini hanya pencatatan ekspor CSV, tidak
     * menyangkut dataset mana pun sehingga tidak punya divisi. Baris seperti
     * itu dicocokkan lewat divisi PELAKUNYA. Tanpa itu, seorang admin tidak
     * akan pernah melihat ekspornya sendiri tercatat, dan jejak audit yang
     * menyembunyikan tindakan orangnya sendiri kehilangan gunanya.
     *
     * <h2>Disambungkan lewat slug</h2>
     *
     * {@code objectSlug} menyimpan slug dataset, dan slug tidak pernah
     * berubah setelah dataset terbit. Jadi sambungan ini tidak akan putus,
     * termasuk untuk dataset yang kemudian dihapus, karena penghapusannya
     * lunak dan barisnya tetap ada.
     *
     * <h2>Slug ikut di dalam query, bukan disaring sesudahnya</h2>
     *
     * Menyaringnya di memori setelah paginasi menghasilkan halaman yang
     * isinya berkurang sementara jumlah totalnya tetap menyebut angka
     * sebelum penyaringan. Yang tampil "halaman 1 dari 9" berisi dua baris,
     * dan delapan halaman berikutnya kosong.
     *
     * Ia juga TIDAK menggantikan penyaringan divisi melainkan menumpang di
     * atasnya. Slug datang dari pemanggil, jadi menjadikannya jalur
     * tersendiri berarti admin mana pun bisa membaca jejak dataset divisi
     * lain hanya dengan menyebut slug-nya.
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:slug IS NULL OR a.objectSlug = :slug)
              AND (:divisionId IS NULL
               OR (a.objectType = 'dataset' AND a.objectSlug IN (
                    SELECT d.slug FROM Dataset d WHERE d.division.id = :divisionId))
               OR (a.objectType <> 'dataset' AND a.actorDivisionCode = :divisionCode))
            ORDER BY a.recordedAt DESC
            """)
    Page<AuditLog> searchForAdmin(@Param("divisionId") UUID divisionId,
            @Param("divisionCode") String divisionCode,
            @Param("slug") String slug,
            Pageable pageable);
}
