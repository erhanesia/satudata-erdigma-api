package id.co.erdigma.satudata.modules.user.port.hris;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
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
 * Sumber data karyawan untuk seluruh profil selain auth-dummy.
 *
 * Baris di tabel {@code users} lokal adalah bayangan, bukan sumber kebenaran:
 * pada login pertama identitas orang itu ditanyakan ke hris-api memakai token
 * miliknya sendiri, lalu disalin ke sini. Tidak ada seed manual, tidak ada
 * kredensial layanan — satu user pool, satu app client, jadi hris-api menilai
 * izinnya persis seperti saat orang itu membuka HRIS.
 */
@Component
@Profile("!auth-dummy")
@Slf4j
public class HrisEmployeeDirectory implements EmployeeDirectory {

    /**
     * ponytail: baris yang lebih muda dari ambang ini dipakai tanpa bertanya ke
     * HRIS. Tanpa ambang, SETIAP permintaan API memicu satu panggilan HTTP.
     * Kalau kesegaran data jadi masalah, jawabannya webhook atau tugas
     * terjadwal dari HRIS — bukan memperkecil ambang ini.
     */
    private static final Duration SEGAR = Duration.ofHours(12);

    private final UserRepository userRepository;
    private final DivisionRepository divisionRepository;
    private final RestClient hris;

    public HrisEmployeeDirectory(
            UserRepository userRepository,
            DivisionRepository divisionRepository,
            RestClient.Builder builder,
            @Value("${hris.base-url}") String baseUrl) {
        this.userRepository = userRepository;
        this.divisionRepository = divisionRepository;
        this.hris = builder.baseUrl(baseUrl).build();
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
        Optional<User> lokal = userRepository.findByCognitoId(cognitoId);
        if (lokal.isPresent() && masihSegar(lokal.get())) {
            return lokal;
        }

        HrisMeResponse jawaban;
        try {
            jawaban = hris.get()
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
            return lokal;
        }

        if (jawaban == null || jawaban.getEmployee() == null) {
            log.warn("Balasan /user/me tanpa data karyawan untuk cognitoId {}", cognitoId);
            return Optional.empty();
        }

        return Optional.of(upsert(lokal.orElse(null), cognitoId, jawaban));
    }

    private boolean masihSegar(User user) {
        return user.getUpdatedAt() != null
                && user.getUpdatedAt().isAfter(LocalDateTime.now().minus(SEGAR));
    }

    private User upsert(User lama, String cognitoId, HrisMeResponse jawaban) {
        User user = (lama != null) ? lama : new User();
        if (lama == null) {
            // cognitoId adalah identitas baris ini dan tidak pernah diubah lagi.
            user.setCognitoId(cognitoId);
        }

        HrisMeResponse.Employee employee = jawaban.getEmployee();
        String position = (employee.getPosition() != null) ? employee.getPosition().getName() : null;

        user.setEmail(jawaban.getEmail());
        user.setName(employee.getName());
        user.setJobLevel(employee.getJobLevel());
        user.setPosition(position);

        // Peran selalu dihitung ulang dari HRIS. Belum ada antarmuka untuk
        // mengubah peran secara manual, jadi tidak ada yang bisa tertimpa;
        // begitu ada, keputusan ini harus ditinjau ulang.
        HrisPermissionLevel level = HrisRoleMapper.permissionLevel(
                jawaban.getRole(), employee.getJobLevel(), position);
        user.setHrisPermissionLevel(level);
        user.setRole(HrisRoleMapper.role(level));

        UUID departementId = (employee.getDepartement() != null) ? employee.getDepartement().getId() : null;
        if (departementId != null) {
            // Divisi hanya ditimpa kalau padanannya ketemu. Kolom
            // division.hris_departement_id masih null untuk kedelapan divisi
            // seed, jadi untuk sementara pengguna baru berdivisi null — itu
            // sudah nullable di sepanjang MeService dan CurrentUserService.
            divisionRepository.findByHrisDepartementIdAndDeletedAtIsNull(departementId)
                    .ifPresent(user::setDivision);
        }

        // Diisi manual: entitas memakai @LastModifiedDate tetapi tidak memasang
        // AuditingEntityListener, jadi nilainya tidak pernah bergerak sendiri —
        // dan masihSegar() di atas bergantung padanya.
        user.setUpdatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }
}
