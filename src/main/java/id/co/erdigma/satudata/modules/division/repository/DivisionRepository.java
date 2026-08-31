package id.co.erdigma.satudata.modules.division.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import id.co.erdigma.satudata.modules.division.entity.Division;

@Repository
public interface DivisionRepository extends JpaRepository<Division, UUID>, JpaSpecificationExecutor<Division> {

    /**
     * Satu-satunya cara mengambil divisi, dan urutannya menentukan peringkat di
     * beranda maupun di halaman Divisi.
     *
     * Unduhan DIHITUNG di sini, tidak dibaca dari kolom. Dulu ada
     * {@code division.downloads}, tetapi tidak ada satu pun kode yang pernah
     * menulisinya — {@code DownloadService} hanya menaikkan
     * {@code dataset.downloads}. Selama isinya angka desain kematian kolom itu
     * tidak kelihatan; begitu changeset 42 mengisinya 0, satu unduhan sungguhan
     * menaikkan angka di panel admin sementara halaman ini tetap 0. Kolomnya
     * dicabut di changeset 43.
     *
     * Sumbernya {@code SUM(dataset.downloads)}, bukan {@code COUNT(download_log)}:
     * tabel log juga memuat baris pratinjau bertanda {@code PREVIEW}, sehingga
     * menghitungnya butuh penyaringan yang gampang terlupa. Memakai penghitung
     * tingkat dataset juga menjamin halaman ini dan panel admin menyebut
     * bilangan yang sama.
     *
     * {@code LEFT JOIN} wajib: divisi tanpa dataset harus tetap muncul dengan
     * nol, bukan hilang dari daftar. Urutan kedua memakai nama supaya ke-25
     * divisi bernilai nol tidak berpindah-pindah tempat tiap kali dimuat.
     *
     * @return baris {@code [Division, Long]} — entitas dan jumlah unduhannya
     */
    @Query("""
            SELECT d, COALESCE(SUM(ds.downloads), 0)
              FROM Division d
              LEFT JOIN Dataset ds ON ds.division = d AND ds.deletedAt IS NULL
             WHERE d.deletedAt IS NULL
             GROUP BY d
             ORDER BY COALESCE(SUM(ds.downloads), 0) DESC, d.name ASC
            """)
    List<Object[]> findAllWithDownloads();

    long countByDeletedAtIsNull();

    Optional<Division> findByHrisTeamIdAndDeletedAtIsNull(UUID hrisTeamId);
}
