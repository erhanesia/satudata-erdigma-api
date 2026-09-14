package id.co.erdigma.satudata.modules.dataset.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import id.co.erdigma.satudata.modules.dataset.entity.DatasetResource;

@Repository
public interface DatasetResourceRepository
        extends JpaRepository<DatasetResource, UUID>, JpaSpecificationExecutor<DatasetResource> {

    /**
     * Ukuran SESUNGGUHNYA seluruh berkas hidup milik satu dataset.
     *
     * Yang dijumlahkan kolom {@code sizeBytes}, dan kolom itu selalu berisi
     * ukuran berkas seperti yang akan diterima orang saat mengunduh, bukan
     * ukurannya di penyimpanan maupun ukurannya saat terkirim.
     *
     * Dipakai menegakkan batas total setelah berkasnya benar-benar
     * tersimpan. Lihat alasannya di DatasetFileService.
     */
    @Query("""
            SELECT COALESCE(SUM(r.sizeBytes), 0) FROM DatasetResource r
            WHERE r.dataset.id = :datasetId AND r.deletedAt IS NULL
            """)
    long sumSizeBytes(@Param("datasetId") UUID datasetId);

    /**
     * Diurutkan menurut jenis berkas — CSV, XLSX, PDF, DOCX — mengikuti
     * sort_order di tabel format, lalu nama berkas sebagai pemutus seri.
     *
     * Urutan ini menentukan urutan tab di Data Explorer, jadi ia harus tetap
     * DAN bermakna. Tanpa ORDER BY, tab yang sama berpindah tempat antar-
     * pemuatan tanpa sebab yang bisa dijelaskan. Mengurutkan menurut waktu
     * pendaftaran juga tidak cukup: berkas dataset contoh didaftarkan dengan
     * timestamp yang sama persis, sehingga urutannya jatuh ke abjad dan berkas
     * pendamping bisa mendahului berkas datanya sendiri.
     */
    List<DatasetResource> findAllByDatasetIdAndDeletedAtIsNullOrderByFormatSortOrderAscFileNameAsc(
            UUID datasetId);

    Optional<DatasetResource> findFirstByDatasetIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID datasetId);

    /**
     * Berkas milik banyak dataset sekaligus.
     *
     * Dipakai daftar panel admin, yang menampilkan lencana dan ukuran berkas di
     * setiap baris. Memanggil versi satu-dataset di dalam perulangan akan
     * menghasilkan satu query per baris — 50 query untuk satu halaman.
     */
    List<DatasetResource> findAllByDatasetIdInAndDeletedAtIsNullOrderByFormatSortOrderAscFileNameAsc(
            List<UUID> datasetIds);

    /**
     * Seluruh berkas milik satu dataset, TERMASUK yang sudah ditandai terhapus.
     *
     * Satu-satunya pemakainya adalah pemilihan nama berkas baru saat dataset
     * disunting, dan justru baris terhapus itulah alasannya ada. Penghapusan di
     * sini bersifat lunak: barisnya tinggal, dan berkasnya masih menempati
     * kuncinya di penyimpanan. Kalau nama baru dipilih hanya dengan melihat
     * berkas yang masih hidup, sebuah unggahan bisa mendapat nama milik berkas
     * terhapus dan MENIMPA isinya di penyimpanan -- tanpa satu pun galat, karena
     * penyimpanan objek dengan senang hati menulis ulang kunci yang sudah ada.
     */
    List<DatasetResource> findAllByDatasetId(UUID datasetId);
}
