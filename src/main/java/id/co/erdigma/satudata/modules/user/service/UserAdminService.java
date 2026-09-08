package id.co.erdigma.satudata.modules.user.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.modules.division.mapper.DivisionMapper;
import id.co.erdigma.satudata.modules.user.dto.UserAdminResponse;
import id.co.erdigma.satudata.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;
    private final DivisionMapper divisionMapper;

    @Transactional(readOnly = true)
    public Page<UserAdminResponse> daftar(String q, Pageable pageable) {
        // Kunci kosong disamakan dengan tanpa kunci: kotak pencarian yang baru
        // dikosongkan mengirim string kosong, dan LIKE '%%' menyaring apa pun.
        String kunci = (q == null || q.isBlank()) ? null : q.trim();
        return userRepository.cariAktif(kunci, pageable).map(this::toResponse);
    }

    public UserAdminResponse toResponse(User user) {
        UserAdminResponse response = new UserAdminResponse();
        response.setId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setPosition(user.getPosition());
        if (user.getDivision() != null) {
            response.setDivision(divisionMapper.toResponseLite(user.getDivision()));
        }
        response.setRole(user.getRole());
        response.setHrisPermissionLevel(user.getHrisPermissionLevel());
        response.setRoleOverride(user.getRoleOverride());
        response.setRoleOverrideAt(user.getRoleOverrideAt());
        return response;
    }
}
