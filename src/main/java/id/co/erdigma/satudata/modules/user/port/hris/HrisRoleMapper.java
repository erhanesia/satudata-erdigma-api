package id.co.erdigma.satudata.modules.user.port.hris;

import java.util.List;

import id.co.erdigma.satudata.enums.HrisPermissionLevel;
import id.co.erdigma.satudata.enums.Role;

import lombok.extern.slf4j.Slf4j;

/**
 * Menerjemahkan jenjang jabatan HRIS menjadi peran portal.
 *
 * Daftar jenjang di bawah adalah salinan PermissionLevelHelper milik hris-api
 * (modules/task/helper/PermissionLevelHelper.java). Disalin, bukan dipanggil,
 * karena GET /api/v1/user/me tidak mengembalikan tingkat izin yang sudah jadi —
 * hanya bahan mentahnya. Kalau HRIS menambah nama jenjang baru, berkas ini yang
 * harus menyusul; log peringatan di bawah yang memberi tahu.
 */
@Slf4j
public final class HrisRoleMapper {

    private static final List<String> DIRECTOR_LEVELS = List.of(
            "Direktur",
            "Direktur Utama",
            "General Manager");

    private static final List<String> MANAGER_LEVELS = List.of(
            "Manager",
            "Junior Manager",
            "Senior Manager",
            "Supervisor",
            "Coordinator");

    private static final List<String> STAFF_LEVELS = List.of(
            "Specialist",
            "Non Staff",
            "Staff");

    private static final String CORPORATE_SECRETARY_POSITION = "Corporate Secretary";

    private HrisRoleMapper() {
    }

    /**
     * @param hrisRole nilai enum Role milik hris-api sebagai teks, mis. "ADMIN"
     * @param jobLevel employee.jobLevel dari hris-api, boleh null
     * @param position employee.position.name dari hris-api, boleh null
     */
    public static HrisPermissionLevel permissionLevel(String hrisRole, String jobLevel, String position) {
        // List.of(...).contains(null) melempar NullPointerException, jadi null
        // dinormalkan lebih dulu alih-alih dijaga di tiap pemeriksaan.
        String jenjang = (jobLevel != null) ? jobLevel : "";

        if ("ADMIN".equals(hrisRole)) {
            return HrisPermissionLevel.ADMIN;
        }
        if (DIRECTOR_LEVELS.contains(jenjang)) {
            return HrisPermissionLevel.DIRECTOR;
        }
        if (STAFF_LEVELS.contains(jenjang) && CORPORATE_SECRETARY_POSITION.equals(position)) {
            return HrisPermissionLevel.CORPORATE_SECRETARY;
        }
        if (MANAGER_LEVELS.contains(jenjang)) {
            return HrisPermissionLevel.MANAGER;
        }
        if (STAFF_LEVELS.contains(jenjang)) {
            return HrisPermissionLevel.STAFF;
        }

        // hris-api melempar IllegalArgumentException di titik ini. Di sini tidak:
        // menolak karyawan sah gara-gara HR menambah nama jenjang baru lebih
        // merugikan daripada memberinya hak terendah. Log-nya yang jadi alarm.
        log.warn("Jenjang jabatan '{}' tidak dikenal — diberi STAFF. Perbarui daftar di HrisRoleMapper.",
                jobLevel);
        return HrisPermissionLevel.STAFF;
    }

    /**
     * Pemetaan ke peran portal. Bukan karangan: dibaca dari tabel sepuluh
     * identitas dummy di features/auth/model/types.ts, supaya mode dummy dan
     * mode Cognito memberi peran yang sama untuk orang yang sama.
     */
    public static Role role(HrisPermissionLevel level) {
        return switch (level) {
            case ADMIN, DIRECTOR -> Role.ADMIN;
            case CORPORATE_SECRETARY, MANAGER -> Role.PUBLISHER;
            case STAFF -> Role.STAFF;
        };
    }
}
