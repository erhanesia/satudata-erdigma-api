package id.co.erdigma.satudata.enums;

/**
 * Tingkat izin sebagaimana dihitung HRIS. Nilainya BUKAN kolom di database HRIS —
 * di sana diturunkan saat runtime dari {@code employee.job_level} dan
 * {@code position} (lihat PermissionLevelHelper di hris-api).
 *
 * Direkam di sini sebagai bahan pemetaan ke {@link Role} milik portal, dan
 * supaya alur otorisasi bisa diuji sebelum integrasi. Saat integrasi nanti,
 * nilai ini sebaiknya DIMINTA dari API HRIS, bukan dihitung ulang di sini —
 * logika turunannya bisa berubah sewaktu-waktu di sisi HRIS.
 */
public enum HrisPermissionLevel {
    ADMIN,
    DIRECTOR,
    CORPORATE_SECRETARY,
    MANAGER,
    STAFF
}
