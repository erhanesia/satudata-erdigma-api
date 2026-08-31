package id.co.erdigma.satudata.exception;

/**
 * Pengguna sudah dikenali, tapi tidak berhak melihat sumber daya ini.
 *
 * Dipisahkan dari {@code AuthorizationDeniedException} milik Spring Security,
 * yang dilempar {@code @PreAuthorize} untuk urusan PERAN. Yang ini soal
 * pembatasan per baris data — posisi jabatan versus tag pada satu dataset —
 * dan pesannya menyebutkan posisi apa yang dibutuhkan. Menyatukan keduanya
 * membuat pesan spesifik itu ikut terkirim pada penolakan peran, yang tidak
 * ada hubungannya.
 *
 * Dipetakan ke HTTP 403 di {@link GlobalExceptionHandler}.
 */
public class AccessNotAllowedException extends RuntimeException {

    public AccessNotAllowedException(String message) {
        super(message);
    }
}
