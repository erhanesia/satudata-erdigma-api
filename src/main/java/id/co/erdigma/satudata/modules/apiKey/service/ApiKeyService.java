package id.co.erdigma.satudata.modules.apiKey.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.enums.ApiKeyStatus;
import id.co.erdigma.satudata.exception.BusinessValidationException;
import id.co.erdigma.satudata.exception.ResourceNotFoundException;
import id.co.erdigma.satudata.modules.apiKey.dto.ApiKeyCreatedResponse;
import id.co.erdigma.satudata.modules.apiKey.dto.ApiKeyRequestCreateDTO;
import id.co.erdigma.satudata.modules.apiKey.dto.ApiKeyResponse;
import id.co.erdigma.satudata.modules.apiKey.dto.ApiKeyRevealResponse;
import id.co.erdigma.satudata.modules.apiKey.entity.ApiKey;
import id.co.erdigma.satudata.modules.apiKey.helper.ApiKeyCipher;
import id.co.erdigma.satudata.modules.apiKey.mapper.ApiKeyMapper;
import id.co.erdigma.satudata.modules.apiKey.repository.ApiKeyRepository;

import lombok.RequiredArgsConstructor;

/**
 * Kredensial untuk pemanggil non-manusia.
 *
 * PENYIMPANAN NILAI KUNCI — keputusan tim, dengan konsekuensi yang dipahami.
 * Desain memuat tombol "Tampilkan" yang mengungkap nilai key secara utuh. Itu
 * hanya mungkin kalau server menyimpan nilainya, jadi sejak changeset 00022
 * kunci disimpan terenkripsi AES-256-GCM ({@link ApiKeyCipher}) di samping hash
 * SHA-256-nya.
 *
 * Konsekuensinya nyata dan harus disadari: siapa pun yang memegang database
 * DAN rahasia enkripsi bisa membaca seluruh API key. Karena itu rahasianya wajib
 * datang dari env var, dan aplikasi menolak start bila kosong.
 *
 * {@code keyHash} tetap dipertahankan dan tetap menjadi dasar verifikasi saat
 * autentikasi mesin dibangun — membandingkan hash lebih murah daripada
 * mendekripsi, dan berarti jalur autentikasi tidak perlu menyentuh rahasia
 * enkripsi sama sekali.
 */
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final String KEY_PREFIX = "erd_live_";
    private static final int MAX_ACTIVE_KEYS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private ApiKeyRepository apiKeyRepository;
    @Autowired
    private ApiKeyMapper apiKeyMapper;
    @Autowired
    private ApiKeyCipher apiKeyCipher;

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> getAll(User user) {
        return apiKeyRepository.findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(user.getId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ApiKeyCreatedResponse create(User user, ApiKeyRequestCreateDTO body) {
        long active = apiKeyRepository.findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(user.getId())
                .stream().filter(k -> k.getStatus() == ApiKeyStatus.ACTIVE).count();
        if (active >= MAX_ACTIVE_KEYS) {
            throw new BusinessValidationException(
                    "Maksimal " + MAX_ACTIVE_KEYS + " key aktif. Cabut salah satu sebelum membuat yang baru.");
        }

        byte[] raw = new byte[16];
        RANDOM.nextBytes(raw);
        String plainKey = KEY_PREFIX + HexFormat.of().formatHex(raw);

        ApiKey key = new ApiKey();
        key.setUser(user);
        key.setName(body.getName());
        key.setKeyHash(sha256(plainKey));
        key.setKeyPrefix(plainKey.substring(0, KEY_PREFIX.length() + 4));

        byte[] nonce = apiKeyCipher.newNonce();
        key.setKeyCipher(apiKeyCipher.encrypt(plainKey, nonce));
        key.setKeyNonce(apiKeyCipher.encode(nonce));

        key.setTier("STANDARD");
        key.setRateLimitPerMinute(5000);
        key.setStatus(ApiKeyStatus.ACTIVE);
        apiKeyRepository.save(key);

        ApiKeyCreatedResponse response = new ApiKeyCreatedResponse();
        response.setKey(toResponse(key));
        response.setPlainKey(plainKey);
        return response;
    }

    /**
     * Mengungkap nilai kunci untuk tombol "Tampilkan".
     *
     * Pencarian selalu disaring {@code userId}, sehingga tidak ada cara meminta
     * kunci milik orang lain walaupun UUID-nya diketahui — kunci orang lain
     * tampak seperti tidak ada, bukan seperti ditolak.
     *
     * Kunci yang dicabut tetap boleh diungkap: pemiliknya berhak tahu nilai apa
     * yang harus dicari dan dibersihkan dari skrip yang masih memakainya.
     */
    @Transactional(readOnly = true)
    public ApiKeyRevealResponse reveal(User user, UUID id) {
        ApiKey key = apiKeyRepository.findByIdAndUserIdAndDeletedAtIsNull(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("API key not found: " + id));

        if (key.getKeyCipher() == null || key.getKeyNonce() == null) {
            throw new BusinessValidationException(
                    "Nilai kunci ini tidak tersimpan karena dibuat sebelum fitur Tampilkan ada. "
                            + "Cabut kunci ini dan buat penggantinya bila nilainya dibutuhkan.");
        }

        ApiKeyRevealResponse response = new ApiKeyRevealResponse();
        response.setPlainKey(apiKeyCipher.decrypt(key.getKeyCipher(), key.getKeyNonce()));
        return response;
    }

    @Transactional
    public void revoke(User user, UUID id) {
        ApiKey key = apiKeyRepository.findByIdAndUserIdAndDeletedAtIsNull(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("API key not found: " + id));

        if (key.getStatus() == ApiKeyStatus.REVOKED) {
            throw new BusinessValidationException("Key ini sudah dicabut sebelumnya.");
        }
        key.setStatus(ApiKeyStatus.REVOKED);
        key.setRevokedAt(LocalDateTime.now());
        apiKeyRepository.save(key);
    }

    private ApiKeyResponse toResponse(ApiKey key) {
        ApiKeyResponse response = apiKeyMapper.toResponse(key);
        response.setMasked(key.getKeyPrefix() + "••••••••••••••••");
        return response;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 tidak tersedia", e);
        }
    }
}
