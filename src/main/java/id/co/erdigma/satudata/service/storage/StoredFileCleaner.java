package id.co.erdigma.satudata.service.storage;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Menghapus berkas yang sudah telanjur naik ke penyimpanan, kalau transaksinya
 * batal.
 *
 * <h2>Masalah yang diselesaikan</h2>
 *
 * Unggahan menulis ke dua tempat yang tidak saling mengenal: berkasnya ke S3,
 * catatannya ke Postgres. Postgres punya transaksi, S3 tidak. Kalau impor
 * barisnya gagal di tengah jalan, seluruh baris database dibatalkan — dataset,
 * kolom, isi tabel — sementara berkasnya tetap duduk di S3 tanpa satu pun baris
 * yang merujuknya.
 *
 * Berkas yatim seperti itu tidak terlihat di portal dan tidak membahayakan apa
 * pun, tetapi memakan ruang dan menumpuk diam-diam. Lebih buruk lagi, ia
 * menempati kunci yang sama dengan yang akan dipakai unggahan ulang dataset
 * bernama sama, sehingga percobaan berikutnya menimpa berkas yang tidak jelas
 * asal-usulnya.
 *
 * <h2>Kenapa lewat sinkronisasi transaksi</h2>
 *
 * Blok {@code try/catch} di sekitar unggahan tidak cukup. Transaksi bisa batal
 * SETELAH metode unggahnya selesai — kegagalan saat commit, atau pembatalan yang
 * dipicu lapisan di atasnya — dan pada saat itu tidak ada lagi blok catch yang
 * berjalan. Sinkronisasi transaksi dipanggil Spring pada akhir transaksi, apa pun
 * yang menyebabkannya berakhir.
 *
 * <h2>Hanya menghapus pada pembatalan yang pasti</h2>
 *
 * {@code STATUS_UNKNOWN} sengaja tidak ikut dihapus. Status itu berarti Spring
 * sendiri tidak tahu transaksinya jadi disimpan atau dibatalkan, dan menghapus
 * berkas dalam keadaan ragu berarti mungkin menghapus berkas milik dataset yang
 * sebenarnya berhasil terbit. Gagal ke arah menyimpan: berkas yatim hanya
 * memakan ruang, berkas hilang membuat dataset yang terlihat sehat tidak bisa
 * diunduh sama sekali.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StoredFileCleaner {

    private final FileStorage fileStorage;

    /**
     * Menandai satu berkas untuk dihapus kalau transaksi yang sedang berjalan
     * dibatalkan.
     *
     * Aman dipanggil di luar transaksi — kalau tidak ada transaksi, tidak ada
     * yang bisa batal, dan penandaan ini tidak melakukan apa-apa.
     */
    public void deleteOnRollback(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }
                try {
                    fileStorage.delete(storageKey);
                    log.info("Transaksi dibatalkan, berkas {} ikut dihapus dari penyimpanan.", storageKey);
                } catch (Exception e) {
                    // Kegagalan pembersihan TIDAK boleh dilempar. Kita sedang
                    // berada di akhir transaksi yang sudah batal; melempar dari
                    // sini hanya menukar galat asli — yang menjelaskan kenapa
                    // unggahannya gagal — dengan galat pembersihan yang tidak
                    // memberi tahu penerbit apa pun.
                    log.warn("Berkas {} gagal dihapus setelah pembatalan. "
                            + "Berkas ini jadi yatim di penyimpanan.", storageKey, e);
                }
            }
        });
    }
}
