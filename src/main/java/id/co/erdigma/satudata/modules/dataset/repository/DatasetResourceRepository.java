package id.co.erdigma.satudata.modules.dataset.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import id.co.erdigma.satudata.modules.dataset.entity.DatasetResource;

@Repository
public interface DatasetResourceRepository
        extends JpaRepository<DatasetResource, UUID>, JpaSpecificationExecutor<DatasetResource> {

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
}
