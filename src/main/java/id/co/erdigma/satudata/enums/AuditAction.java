package id.co.erdigma.satudata.enums;

/**
 * Tindakan yang tercatat di jejak audit.
 *
 * Tujuh nilai ini mengikuti desain panel admin. Yang benar-benar dipancarkan
 * kode saat ini baru {@link #CREATE} — dari penerbitan dataset. Sisanya sudah
 * didefinisikan karena alur tinjauan (SUBMIT → PUBLISH/REJECT) dan pengarsipan
 * memang direncanakan, dan menambah nilai enum belakangan lebih murah daripada
 * mengubah nilai yang sudah tersimpan di database.
 *
 * Baris audit dummy memakai seluruh nilai supaya tampilan dan pewarnaannya bisa
 * diperiksa sebelum alurnya ada.
 */
public enum AuditAction {
    /** Dataset baru dibuat. */
    CREATE,
    /** Metadata atau berkas dataset diubah. */
    UPDATE,
    /** Diajukan penerbit untuk ditinjau. */
    SUBMIT,
    /** Disetujui dan terbit. */
    PUBLISH,
    /** Ditolak peninjau; kembali ke penerbit. */
    REJECT,
    /** Tidak lagi aktif, tapi tetap bisa dilihat. */
    ARCHIVE,
    /** Dihapus (soft delete). */
    DELETE
}
