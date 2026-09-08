package id.co.erdigma.satudata.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Cerminan enum {@code JobLevel} milik hris-api.
 *
 * Menggantikan {@code JobPosition} yang berisi sembilan label karangan dari
 * berkas desain. Label itu disusun sebelum siapa pun tahu isi HRIS, dan mencampur
 * dua sumbu yang di HRIS justru dipisah tegas: senioritas ada di {@code jobLevel},
 * peran fungsional ada di tabel {@code position}.
 *
 * <h2>Kenapa disalin, bukan diambil lewat API</h2>
 *
 * Kedua belas nilai ini ada di KODE hris-api, bukan di tabel. Ia tidak bisa
 * berubah tanpa deploy ulang HRIS, jadi tidak ada risiko menyimpang seperti yang
 * terjadi pada daftar team dan posisi. Memanggil API untuk sesuatu yang tetap
 * hanya menambah satu titik gagal tanpa menambah kebenaran.
 *
 * Urutannya dari yang paling senior, dan itu disengaja: antarmuka menampilkannya
 * apa adanya, sehingga admin membaca daftar yang tersusun seperti struktur
 * organisasi, bukan seperti abjad.
 *
 * <h2>Kenapa labelnya, bukan nama enumnya, yang disimpan</h2>
 *
 * HRIS memasang {@code @JsonValue} pada label, sehingga {@code /me} mengirim
 * "Senior Manager" dan bukan "SENIOR_MANAGER". Nilai itulah yang tersimpan di
 * {@code users.job_level}, jadi aturan akses harus memakai bentuk yang sama agar
 * bisa dibandingkan tanpa penerjemahan di tengah jalan — dan penerjemahan di
 * tengah jalan adalah tempat kesalahan bersembunyi.
 */
public enum HrisJobLevel {
    DIREKTUR_UTAMA("Direktur Utama"),
    DIREKTUR("Direktur"),
    GENERAL_MANAGER("General Manager"),
    SENIOR_MANAGER("Senior Manager"),
    MANAGER("Manager"),
    JUNIOR_MANAGER("Junior Manager"),
    SUPERVISOR("Supervisor"),
    COORDINATOR("Coordinator"),
    SPECIALIST("Specialist"),
    STAFF("Staff"),
    NON_STAFF("Non Staff"),
    ADMIN("Admin");

    private final String label;

    HrisJobLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Urutannya dari yang paling senior, sesuai urutan deklarasi di atas. */
    public static List<String> labels() {
        return Arrays.stream(values()).map(HrisJobLevel::getLabel).toList();
    }

    /**
     * Mengembalikan null bila tidak dikenal, bukan melempar. Pemanggil yang
     * memutuskan apakah label asing itu galat isian atau sekadar diabaikan.
     */
    public static HrisJobLevel fromLabel(String label) {
        if (label == null) {
            return null;
        }
        String cleaned = label.trim().toLowerCase(Locale.ROOT);
        for (HrisJobLevel level : values()) {
            if (level.label.toLowerCase(Locale.ROOT).equals(cleaned)) {
                return level;
            }
        }
        return null;
    }
}
