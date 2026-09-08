package id.co.erdigma.satudata.modules.user.dto;

import id.co.erdigma.satudata.enums.Role;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

@Data
public class UserRoleUpdateRequest {

    /**
     * Peran yang ditunjuk. `null` — juga bila ruasnya tidak dikirim sama sekali —
     * berarti kembali mengikuti HRIS.
     */
    @Schema(description = "Peran yang ditunjuk. null berarti kembali mengikuti HRIS.", nullable = true)
    private Role role;
}
