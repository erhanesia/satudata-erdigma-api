package id.co.erdigma.satudata.enums;

import java.util.List;
import java.util.Locale;

/**
 * Daftar posisi jabatan yang bisa dipakai membatasi akses sebuah dataset.
 *
 * SEMENTARA. Daftar yang sesungguhnya milik HRIS, bukan portal ini — sembilan
 * nilai di bawah diambil dari desain panel admin supaya isian dan penyaringnya
 * bisa dibangun sekarang. Begitu HRIS tersambung, enum ini diganti pembacaan
 * dari sana, dan kolom {@code dataset_position_access.position} tetap berupa
 * teks justru supaya penggantian itu tidak menuntut migrasi data.
 *
 * Perhatikan ini BERBEDA dari {@code HrisPermissionLevel} dan dari
 * {@code users.job_level}: keduanya soal senioritas, sedangkan ini soal peran
 * fungsional. Seorang "Data Analyst" bisa berjenjang Coordinator maupun Staff.
 */
public enum JobPosition {
    DIREKSI("Direksi"),
    GENERAL_MANAGER("General Manager"),
    MANAGER("Manager"),
    PROJECT_MANAGER("Project Manager"),
    TEAM_LEAD("Team Lead"),
    DATA_ANALYST("Data Analyst"),
    STAFF_SENIOR("Staff Senior"),
    STAFF("Staff"),
    MAGANG("Magang");

    private final String label;

    JobPosition(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Urutannya dari yang paling senior — sama dengan urutan di desain. */
    public static List<String> labels() {
        return java.util.Arrays.stream(values()).map(JobPosition::getLabel).toList();
    }

    /**
     * Mengembalikan null bila tidak dikenal, bukan melempar. Pemanggil yang
     * memutuskan apakah label asing itu galat isian atau sekadar diabaikan.
     */
    public static JobPosition fromLabel(String label) {
        if (label == null) {
            return null;
        }
        String cleaned = label.trim().toLowerCase(Locale.ROOT);
        for (JobPosition position : values()) {
            if (position.label.toLowerCase(Locale.ROOT).equals(cleaned)) {
                return position;
            }
        }
        return null;
    }
}
