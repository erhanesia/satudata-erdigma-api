package id.co.erdigma.satudata.modules.user.service;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.exception.EmployeeResignedException;
import id.co.erdigma.satudata.modules.user.port.EmployeeDirectory;

import jakarta.persistence.EntityManager;

@Service
public class CurrentUserService {

    private final EmployeeDirectory employeeDirectory;
    private final EntityManager entityManager;

    public CurrentUserService(
            EmployeeDirectory employeeDirectory,
            EntityManager entityManager) {
        this.employeeDirectory = employeeDirectory;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public User getCurrentUser(String cognitoId, String username) {
        User user = employeeDirectory.findByCognitoId(cognitoId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Token tetap sah sampai kedaluwarsa, jadi status kepegawaian wajib
        // dicek ulang tiap request — bukan hanya saat login.
        if (user.getDeletedAt() != null) {
            throw new EmployeeResignedException("Employee has resigned and cannot log in.");
        }

        // Divisi dipakai luas oleh pemanggil (audit log, otorisasi per divisi).
        // Relasinya LAZY, jadi harus diinisialisasi SEBELUM detach — kalau tidak,
        // pemanggil akan kena LazyInitializationException di luar sesi Hibernate.
        if (user.getDivision() != null) {
            user.getDivision().getCode();
        }

        entityManager.detach(user);

        return user;
    }
}
