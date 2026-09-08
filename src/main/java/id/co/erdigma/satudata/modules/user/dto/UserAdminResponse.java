package id.co.erdigma.satudata.modules.user.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import id.co.erdigma.satudata.annotation.PrefixedId;
import id.co.erdigma.satudata.enums.HrisPermissionLevel;
import id.co.erdigma.satudata.enums.IdPrefix;
import id.co.erdigma.satudata.enums.Role;
import id.co.erdigma.satudata.modules.division.dto.DivisionResponseLite;

import lombok.Data;

/**
 * Satu baris di panel manajemen pengguna.
 *
 * Berbeda dari {@link UserResponse} yang melayani pil identitas: di sini
 * `roleOverride` ikut dibawa supaya antarmuka bisa membedakan peran hasil
 * tunjukan manusia dari peran hitungan HRIS. Membandingkan `role` dengan
 * `hrisPermissionLevel` tidak bisa dipakai untuk itu — keduanya bisa kebetulan
 * sama padahal sumbernya berbeda.
 */
@Data
public class UserAdminResponse {
    @PrefixedId(IdPrefix.USER)
    private UUID id;
    private String name;
    private String email;
    private String position;
    private DivisionResponseLite division;

    /** Peran efektif — yang benar-benar menentukan hak di portal. */
    private Role role;

    /** Hasil hitungan HRIS. ADMIN di sini berarti admin warisan. */
    private HrisPermissionLevel hrisPermissionLevel;

    /** Null berarti baris ini mengikuti HRIS. */
    private Role roleOverride;
    private LocalDateTime roleOverrideAt;
}
