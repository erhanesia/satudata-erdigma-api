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
     * {@code LEFT JOIN} wajib: divisi yang punya karyawan tetapi belum punya
     * dataset harus tetap muncul dengan nol, bukan hilang dari daftar. Urutan
     * kedua memakai nama supaya divisi bernilai nol tidak berpindah-pindah
     * tempat tiap kali dimuat.
     *
     * <h2>Divisi yang tidak ditampilkan</h2>
     *
     * Sejak changeset 54 tabel ini memuat SELURUH team HRIS produksi, termasuk
     * yang tidak punya karyawan. Barisnya sengaja tetap hidup supaya karyawan
     * yang kelak masuk ke team itu langsung mendapat divisi saat login; yang
     * disaring hanya daftarnya.
     *
     * Divisi tampil kalau punya karyawan ATAU punya dataset yang belum dihapus.
     * Pengecualian kedua menjaga dataset tidak kehilangan tempatnya di daftar
     * divisi ketika team pemiliknya kebetulan kosong. Penyaring divisi di panel
     * admin ikut memakai daftar ini, dan itu aman: divisi yang tersembunyi pasti
     * tidak punya dataset untuk disaring.
     *
     * Syaratnya ditaruh di {@code HAVING}, bukan {@code WHERE}, karena "punya
     * dataset" baru bisa dinilai setelah baris dataset dikelompokkan per divisi.
     * {@code COUNT(ds)} tidak menghitung baris kosong dari {@code LEFT JOIN},
     * jadi divisi tanpa dataset bernilai nol di sana.
     *
     * Pencocokan divisi saat login TIDAK lewat sini, melainkan lewat
     * {@link #findByHrisTeamIdAndDeletedAtIsNull}, dan memang tidak boleh ikut
     * tersaring.
     *
     * @return baris {@code [Division, Long]} — entitas dan jumlah unduhannya
     */
    @Query("""
            SELECT d, COALESCE(SUM(ds.downloads), 0)
              FROM Division d
              LEFT JOIN Dataset ds ON ds.division = d AND ds.deletedAt IS NULL
             WHERE d.deletedAt IS NULL
             GROUP BY d
            HAVING d.hrisEmployeeCount > 0 OR COUNT(ds) > 0
             ORDER BY COALESCE(SUM(ds.downloads), 0) DESC, d.name ASC
            """)
    List<Object[]> findAllWithDownloads();

    long countByDeletedAtIsNull();

    Optional<Division> findByHrisTeamIdAndDeletedAtIsNull(UUID hrisTeamId);
}
