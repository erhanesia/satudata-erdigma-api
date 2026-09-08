package id.co.erdigma.satudata.modules.user.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.HrisPermissionLevel;
import id.co.erdigma.satudata.enums.Role;
import id.co.erdigma.satudata.exception.BusinessValidationException;
import id.co.erdigma.satudata.exception.ResourceNotFoundException;
import id.co.erdigma.satudata.modules.division.mapper.DivisionMapper;
import id.co.erdigma.satudata.modules.user.dto.UserAdminResponse;
import id.co.erdigma.satudata.modules.user.port.hris.HrisRoleMapper;
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

    @Transactional
    public UserAdminResponse ubahPeran(User pemanggil, UUID targetId, Role peranBaru) {
        // Larangan ini sekaligus yang menjamin selalu tersisa satu admin warisan
        // HRIS yang aktif: tidak seorang pun bisa mengunci dirinya sendiri.
        if (pemanggil.getId().equals(targetId)) {
            throw new BusinessValidationException("Tidak bisa mengubah peran sendiri.");
        }

        User target = userRepository.findById(targetId)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Pengguna tidak ditemukan."));

        if (peranBaru == null) {
            target.setRoleOverride(null);
            target.setRoleOverrideBy(null);
            target.setRoleOverrideAt(null);
            // Tidak perlu bertanya ke HRIS: tingkat izin terakhir sudah tersimpan
            // di baris ini. Null hanya mungkin pada baris lawas yang tidak pernah
            // lewat upsert(), dan hak terendah lebih aman daripada melempar.
            HrisPermissionLevel level = target.getHrisPermissionLevel();
            target.setRole(level != null ? HrisRoleMapper.role(level) : Role.STAFF);
        } else {
            target.setRoleOverride(peranBaru);
            target.setRoleOverrideBy(pemanggil.getId());
            target.setRoleOverrideAt(LocalDateTime.now());
            target.setRole(peranBaru);
        }

        // Diisi manual, sama seperti di HrisEmployeeDirectory.upsert():
        // AuditingEntityListener tidak dipasang, jadi @LastModifiedDate diam.
        target.setUpdatedAt(LocalDateTime.now());

        return toResponse(userRepository.save(target));
    }
}
