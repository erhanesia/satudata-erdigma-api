package id.co.erdigma.satudata.modules.user.port.hris;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.HrisPermissionLevel;
import id.co.erdigma.satudata.modules.division.repository.DivisionRepository;
import id.co.erdigma.satudata.modules.user.port.EmployeeDirectory;
import id.co.erdigma.satudata.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Sumber data karyawan untuk seluruh aplikasi.
 *
 * Baris di tabel {@code users} lokal adalah bayangan, bukan sumber kebenaran:
 * pada login pertama identitas orang itu ditanyakan ke hris-api memakai token
 * miliknya sendiri, lalu disalin ke sini. Tidak ada seed manual, tidak ada
 * kredensial layanan — satu user pool, satu app client, jadi hris-api menilai
 * izinnya persis seperti saat orang itu membuka HRIS.
 */
@Component
@Slf4j
public class HrisEmployeeDirectory implements EmployeeDirectory {

    /**
     * ponytail: baris yang lebih muda dari ambang ini dipakai tanpa bertanya ke
     * HRIS. Tanpa ambang, SETIAP permintaan API memicu satu panggilan HTTP.
     * Kalau kesegaran data jadi masalah, jawabannya webhook atau tugas
     * terjadwal dari HRIS — bukan memperkecil ambang ini.
     */
    private static final Duration FRESH_FOR = Duration.ofHours(12);

    private final UserRepository userRepository;
    private final DivisionRepository divisionRepository;
    private final RestClient hris;

    public HrisEmployeeDirectory(
            UserRepository userRepository,
            DivisionRepository divisionRepository,
            RestClient hrisRestClient) {
        this.userRepository = userRepository;
        this.divisionRepository = divisionRepository;
        this.hris = hrisRestClient;
    }

    /**
     * Pencarian tanpa token — dipakai CurrentUserService di tengah permintaan,
     * saat barisnya sudah pasti ada karena converter menjalankan varian di
     * bawah lebih dulu pada permintaan yang sama.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByCognitoId(String cognitoId) {
        return userRepository.findByCognitoId(cognitoId);
    }

    @Override
    @Transactional
    public Optional<User> findByCognitoId(String cognitoId, String accessToken) {
        Optional<User> local = userRepository.findByCognitoId(cognitoId);
        if (local.isPresent() && isFresh(local.get())) {
            return local;
        }

        HrisMeResponse response;
        try {
            response = hris.get()
                    .uri("/user/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(HrisMeResponse.class);
        } catch (HttpClientErrorException e) {
            // 401/403/404 dari HRIS berarti orang ini memang tidak berhak atau
            // tidak ada. Kosong di sini berujung 401 — jalur yang sama dengan
            // karyawan yang sudah resign, dan front-end sudah menanganinya.
            log.info("hris-api menjawab {} untuk cognitoId {}", e.getStatusCode(), cognitoId);
            return Optional.empty();
        } catch (RestClientException e) {
            // HRIS sedang mati atau tak terjangkau. Kalau barisnya sudah ada,
            // yang basi tetap dipakai: memutus seluruh portal karena sistem
            // tetangga sedang tumbang adalah hukuman yang tidak sebanding.
            log.warn("hris-api tidak dapat dihubungi ({}). Memakai baris lokal bila ada.", e.getMessage());
            return local;
        }

        if (response == null || response.getEmployee() == null) {
            log.warn("Balasan /user/me tanpa data karyawan untuk cognitoId {}", cognitoId);
            return Optional.empty();
        }

        return Optional.of(upsert(local.orElse(null), cognitoId, response));
    }

    private boolean isFresh(User user) {
        return user.getUpdatedAt() != null
                && user.getUpdatedAt().isAfter(LocalDateTime.now().minus(FRESH_FOR));
    }

    private User upsert(User existing, String cognitoId, HrisMeResponse response) {
        User user = (existing != null) ? existing : new User();
        if (existing == null) {
            // cognitoId adalah identitas baris ini dan tidak pernah diubah lagi.
            user.setCognitoId(cognitoId);
        }

        HrisMeResponse.Employee employee = response.getEmployee();
        String position = (employee.getPosition() != null) ? employee.getPosition().getName() : null;

        user.setEmail(response.getEmail());
        user.setName(employee.getName());
        user.setJobLevel(employee.getJobLevel());
        user.setPosition(position);

        // Ditimpa setiap kali disegarkan, termasuk saat HRIS mengirim null.
        // Karyawan yang menghapus fotonya di HRIS harus ikut kehilangan fotonya
        // di sini — kalau nilai lama dipertahankan, portal ini akan terus
        // menampilkan foto yang sudah sengaja dicabut orangnya.
        user.setProfileImage(employee.getProfileImage());

        // Kedua pengenal ini yang dipakai DatasetAccessGuard mencocokkan aturan
        // POSITION dan EMPLOYEE. Namanya sudah disimpan di atas untuk
        // ditampilkan, tetapi pencocokan memakai UUID: nama posisi di HRIS
        // memuat salah ketik yang suatu saat diperbaiki, dan pembatasan berbasis
        // nama akan putus diam-diam begitu itu terjadi.
        user.setHrisPositionId(employee.getPosition() != null ? employee.getPosition().getId() : null);
        user.setHrisEmployeeId(employee.getId());

        // Peran selalu dihitung ulang dari HRIS. Belum ada antarmuka untuk
        // mengubah peran secara manual, jadi tidak ada yang bisa tertimpa;
        // begitu ada, keputusan ini harus ditinjau ulang.
        HrisPermissionLevel level = HrisRoleMapper.permissionLevel(
                response.getRole(), employee.getJobLevel(), position);
        user.setHrisPermissionLevel(level);
        user.setRole(HrisRoleMapper.role(level));

        UUID teamId = (employee.getTeam() != null) ? employee.getTeam().getId() : null;
        if (teamId != null) {
            // Divisi hanya ditimpa kalau padanannya ketemu, sehingga divisi yang
            // sudah disetel tangan tidak hilang gara-gara satu team baru di HRIS
            // yang belum ada di tabel `division`.
            //
            // Sebelum changeset 41 yang dicocokkan di sini `departement`, dan
            // padanannya TIDAK PERNAH ketemu karena kolomnya kosong untuk
            // kedelapan divisi desain. Akibatnya setiap pengguna Cognito
            // berdivisi null, dan karena itu tidak bisa menerbitkan dataset
            // sama sekali — DatasetUploadService menolaknya karena tidak ada
            // yang bisa dicatat sebagai penerbit.
            divisionRepository.findByHrisTeamIdAndDeletedAtIsNull(teamId)
                    .ifPresent(user::setDivision);
        }

        // Diisi manual: entitas memakai @LastModifiedDate tetapi tidak memasang
        // AuditingEntityListener, jadi nilainya tidak pernah bergerak sendiri —
        // dan isFresh() di atas bergantung padanya.
        user.setUpdatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }
}
