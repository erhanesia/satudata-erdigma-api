package id.co.erdigma.satudata.modules.user.port.dummy;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.modules.user.port.EmployeeDirectory;
import id.co.erdigma.satudata.repository.UserRepository;

/**
 * Implementasi sementara: membaca user seed dari tabel {@code users}
 * (lihat db.changelog-00010-seed-dummy-user.yaml).
 */
@Component
@Profile("auth-dummy")
public class DummyEmployeeDirectory implements EmployeeDirectory {

    private final UserRepository userRepository;

    public DummyEmployeeDirectory(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByCognitoId(String cognitoId) {
        return userRepository.findByCognitoId(cognitoId);
    }
}
