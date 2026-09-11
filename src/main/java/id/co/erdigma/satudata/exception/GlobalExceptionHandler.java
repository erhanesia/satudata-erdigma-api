package id.co.erdigma.satudata.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(EmployeeResignedException.class)
    public ResponseEntity<Map<String, String>> handleEmployeeResigned(EmployeeResignedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUsernameNotFound(UsernameNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", ex.getMessage()));
    }

    /**
     * Penolakan {@code @PreAuthorize}. Tanpa penangan ini yang keluar adalah
     * halaman galat bawaan Spring lengkap dengan jejak tumpukan — bentuk yang
     * berbeda dari seluruh galat lain di API ini, sehingga front-end tidak bisa
     * membacanya dengan cara yang sama.
     *
     * Pesannya sengaja tidak menyebutkan peran apa yang dibutuhkan: itu
     * informasi tentang struktur otorisasi, dan orang yang memang berhak sudah
     * tahu perannya dari halaman dasbor.
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAuthorizationDenied(AuthorizationDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Anda tidak berhak melakukan tindakan ini."));
    }

    @ExceptionHandler(AccessNotAllowedException.class)
    public ResponseEntity<Map<String, String>> handleAccessNotAllowed(AccessNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(BusinessValidationException.class)
    public ResponseEntity<Map<String, String>> handleBusinessValidation(BusinessValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    /**
     * Menangkap argumen tak masuk akal yang lolos ke lapisan Spring Data —
     * paling sering {@code size=0} pada paginasi, yang dijawab
     * "Page size must not be less than one". Tanpa penangan ini galatnya
     * diteruskan ke /error dan berakhir sebagai 401 yang menyesatkan.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    /**
     * Body permintaan tidak bisa dibaca oleh Jackson — JSON rusak, body kosong,
     * atau nilai enum yang tidak dikenal (mis. {@code {"role": "SUPERADMIN"}}
     * di {@code PATCH /api/v1/users/{id}/role}). Tanpa penangan ini galatnya
     * jatuh ke halaman /error bawaan Spring, bentuk yang berbeda dari
     * {@code {"error": ...}} yang dipakai seluruh 4xx lain di API ini.
     *
     * Pesan galatnya sengaja tetap dan tidak mengutip {@code ex.getMessage()}:
     * pesan Jackson di baliknya bisa memuat nama kelas Java dan potongan
     * payload pengirim, dan itu tidak untuk dilihat klien.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Permintaan tidak valid."));
    }

    /**
     * Unggahan yang ditolak container sebelum kode aplikasi sempat berjalan.
     *
     * <h2>Kenapa penangan ini perlu ada terpisah</h2>
     *
     * DatasetFileService sudah memeriksa ukuran dan memberi pesan yang menyebut
     * nama berkasnya. Tetapi pemeriksaan itu berjalan di dalam controller,
     * sedangkan batas multipart ditegakkan Tomcat saat mengurai permintaan,
     * yaitu sebelum controller mana pun dipanggil. Untuk berkas yang melampaui
     * batas container, pemeriksaan aplikasi tidak pernah sampai giliran.
     *
     * Tanpa penangan ini galatnya jatuh ke penanganan bawaan Spring dan
     * berakhir sebagai 500 "Terjadi gangguan pada server" — kalimat yang
     * menyalahkan sistem untuk sesuatu yang justru bisa diperbaiki sendiri oleh
     * yang mengunggah, dan yang membuatnya melapor alih-alih mengecilkan
     * berkasnya.
     *
     * <h2>Kenapa pesannya menyebut kedua angka</h2>
     *
     * Pada titik ini permintaannya belum diurai, jadi tidak ada cara mengetahui
     * berkas mana yang kebesaran, atau bahkan apakah yang terlampaui batas per
     * berkas atau batas totalnya. Menyebut keduanya lebih jujur daripada
     * menebak salah satu.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("error",
                "Unggahan terlalu besar. Batasnya 15 MB per berkas dan 60 MB untuk"
                        + " seluruh berkas dalam satu dataset."));
    }
}
