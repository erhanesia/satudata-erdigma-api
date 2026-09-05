package id.co.erdigma.satudata.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Menjaga pembersihan berkas agar tidak berbalik arah.
 *
 * Dua kegagalan yang mungkin terjadi di kelas ini punya akibat yang sangat
 * berbeda. Tidak menghapus saat seharusnya menghapus meninggalkan berkas yatim
 * yang memakan ruang — merepotkan. Menghapus saat seharusnya tidak menghapus
 * membuang berkas milik dataset yang berhasil terbit, dan datasetnya jadi
 * terlihat sehat di portal tetapi tidak bisa diunduh sama sekali — jauh lebih
 * parah, dan tidak bisa dibatalkan.
 *
 * Karena itu yang diuji bukan cuma "menghapus saat batal", tetapi juga ketiga
 * keadaan yang TIDAK boleh menghapus.
 */
class StoredFileCleanerTest {

    private final FileStorage storage = mock(FileStorage.class);
    private final StoredFileCleaner cleaner = new StoredFileCleaner(storage);

    @AfterEach
    void clearSynchronizations() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** Menirukan transaksi yang sedang berjalan, lalu menutupnya dengan status tertentu. */
    private void inTransaction(Runnable work, int finalStatus) {
        TransactionSynchronizationManager.initSynchronization();
        work.run();
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(finalStatus);
        }
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @DisplayName("berkas dihapus saat transaksinya dibatalkan")
    void deletesOnRollback() {
        inTransaction(() -> cleaner.deleteOnRollback("dataset/laporan/laporan.csv"),
                TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(storage).delete("dataset/laporan/laporan.csv");
    }

    @Test
    @DisplayName("berkas TIDAK dihapus saat transaksinya berhasil disimpan")
    void keepsOnCommit() {
        inTransaction(() -> cleaner.deleteOnRollback("dataset/laporan/laporan.csv"),
                TransactionSynchronization.STATUS_COMMITTED);

        verify(storage, never()).delete(anyString());
    }

    @Test
    @DisplayName("berkas TIDAK dihapus saat akhir transaksinya tidak diketahui")
    void keepsOnUnknownStatus() {
        // STATUS_UNKNOWN berarti Spring sendiri tidak tahu jadi disimpan atau
        // dibatalkan. Menghapus dalam keadaan ragu berarti mungkin membuang
        // berkas milik dataset yang sebenarnya terbit.
        inTransaction(() -> cleaner.deleteOnRollback("dataset/laporan/laporan.csv"),
                TransactionSynchronization.STATUS_UNKNOWN);

        verify(storage, never()).delete(anyString());
    }

    @Test
    @DisplayName("di luar transaksi tidak melakukan apa-apa dan tidak melempar")
    void safeOutsideTransaction() {
        // Terjadi pada jalur seperti seeder yang berjalan tanpa transaksi. Tidak
        // ada yang bisa dibatalkan, jadi tidak ada yang perlu dibersihkan.
        cleaner.deleteOnRollback("dataset/laporan/laporan.csv");

        verify(storage, never()).delete(anyString());
    }

    @Test
    @DisplayName("kunci kosong diabaikan")
    void ignoresBlankKey() {
        inTransaction(() -> {
            cleaner.deleteOnRollback(null);
            cleaner.deleteOnRollback("   ");
        }, TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(storage, never()).delete(anyString());
    }

    @Test
    @DisplayName("kegagalan menghapus tidak dilempar keluar")
    void swallowsDeleteFailure() {
        // Kita sedang berada di akhir transaksi yang sudah batal. Melempar dari
        // sini menukar galat asli — yang menjelaskan kenapa unggahannya gagal —
        // dengan galat pembersihan yang tidak memberi tahu penerbit apa pun.
        doThrow(new RuntimeException("S3 sedang tidak bisa dihubungi"))
                .when(storage).delete(anyString());

        inTransaction(() -> cleaner.deleteOnRollback("dataset/laporan/laporan.csv"),
                TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(storage).delete("dataset/laporan/laporan.csv");
    }

    @Test
    @DisplayName("beberapa berkas dalam satu transaksi dihapus semuanya")
    void deletesEveryFile() {
        // Satu dataset bisa punya beberapa berkas — CSV plus PDF, misalnya — dan
        // pembatalan harus membersihkan seluruhnya, bukan yang terakhir saja.
        inTransaction(() -> {
            cleaner.deleteOnRollback("dataset/laporan/laporan.csv");
            cleaner.deleteOnRollback("dataset/laporan/laporan-2.pdf");
        }, TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(storage).delete("dataset/laporan/laporan.csv");
        verify(storage).delete("dataset/laporan/laporan-2.pdf");
    }

    @Test
    @DisplayName("sinkronisasi terdaftar tepat satu per berkas")
    void registersOneSynchronizationPerFile() {
        TransactionSynchronizationManager.initSynchronization();

        cleaner.deleteOnRollback("dataset/laporan/laporan.csv");
        cleaner.deleteOnRollback("dataset/laporan/laporan-2.pdf");

        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(2);
    }
}
