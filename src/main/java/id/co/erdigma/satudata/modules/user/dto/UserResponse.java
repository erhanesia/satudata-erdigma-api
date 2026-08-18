package id.co.erdigma.satudata.modules.user.dto;

import id.co.erdigma.satudata.annotation.PrefixedId;
import id.co.erdigma.satudata.enums.IdPrefix;
import java.util.UUID;

import id.co.erdigma.satudata.enums.HrisPermissionLevel;
import id.co.erdigma.satudata.enums.Role;
import id.co.erdigma.satudata.modules.division.dto.DivisionResponseLite;

import lombok.Data;

/**
 * Identitas pengguna yang sedang masuk — dipakai header di setiap halaman
 * (nama, jabatan, inisial avatar).
 *
 * Tidak pernah memuat cognitoId. Nilai itu identitas internal untuk audit,
 * bukan sesuatu yang perlu diketahui browser.
 */
@Data
public class UserResponse {
    @PrefixedId(IdPrefix.USER)
    private UUID id;
    private String name;
    private String email;
    private String position;
    private String initials;
    private Role role;
    private HrisPermissionLevel hrisPermissionLevel;
    private String jobLevel;
    private DivisionResponseLite division;
}
