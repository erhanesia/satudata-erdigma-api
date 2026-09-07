package id.co.erdigma.satudata.enums;

/**
 * Jenis aturan pembatasan akses sebuah dataset.
 *
 * Ketiganya berdiri sejajar dan dinilai dengan cara yang sama: dataset terlihat
 * bila SALAH SATU aturannya cocok. Tidak ada urutan kewenangan di antara
 * ketiganya — aturan EMPLOYEE tidak lebih kuat daripada JOB_LEVEL, ia hanya
 * lebih sempit.
 *
 * Isi {@code rule_value} berbeda per jenis, dan itu disengaja:
 *
 * <ul>
 *   <li>{@link #JOB_LEVEL} — label jenjang jabatan HRIS, mis. {@code Senior Manager}
 *       — bukan nama enumnya, {@code SENIOR_MANAGER} akan ditolak.
 *       Dipakai apa adanya karena enum itu ada di KODE hris-api, bukan di tabel,
 *       sehingga tidak bisa berubah tanpa deploy ulang HRIS.</li>
 *   <li>{@link #POSITION} — UUID posisi milik HRIS, bukan namanya. Tabel posisi
 *       HRIS memuat salah ketik seperti "HO Customer Acquisiton"; begitu
 *       diperbaiki, pembatasan berbasis nama putus tanpa galat.</li>
 *   <li>{@link #EMPLOYEE} — UUID karyawan milik HRIS. Untuk menunjuk orang
 *       tertentu tanpa memedulikan jabatannya.</li>
 * </ul>
 */
public enum AccessRuleType {
    JOB_LEVEL,
    POSITION,
    EMPLOYEE
}
