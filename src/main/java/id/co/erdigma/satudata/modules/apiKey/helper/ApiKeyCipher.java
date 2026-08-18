package id.co.erdigma.satudata.modules.apiKey.helper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Enkripsi nilai API key supaya bisa ditampilkan ulang di Dasbor Akun.
 *
 * Memakai AES-256-GCM: mode berotentikasi, sehingga ciphertext yang diubah di
 * database akan ditolak saat dekripsi, bukan menghasilkan sampah diam-diam.
 *
 * Nonce dibuat acak 12 byte untuk SETIAP enkripsi dan disimpan bersama
 * ciphertext-nya. Ini wajib pada GCM — memakai ulang pasangan kunci+nonce
 * merusak jaminan keamanannya secara total, bukan sekadar melemahkan.
 *
 * Kunci enkripsi diturunkan dengan SHA-256 dari rahasia di env var, sehingga
 * panjang rahasianya bebas sementara kunci AES-nya tetap tepat 256 bit.
 */
@Component
public class ApiKeyCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BIT = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String secret;
    private SecretKeySpec keySpec;

    public ApiKeyCipher(@Value("${satudata.api-key.encryption-secret:}") String secret) {
        this.secret = secret;
    }

    /**
     * Gagal saat startup, bukan saat pengguna menekan tombol.
     *
     * Rahasia yang kosong berarti setiap pembuatan API key akan meledak di
     * tengah jalan — lebih baik aplikasinya menolak menyala dengan pesan yang
     * menjelaskan apa yang kurang.
     */
    @PostConstruct
    public void init() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "Properti satudata.api-key.encryption-secret kosong. "
                            + "Isi lewat env var APIKEY_ENCRYPTION_SECRET — nilai API key "
                            + "disimpan terenkripsi dan tidak bisa dibuat tanpa rahasia ini.");
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 tidak tersedia", e);
        }
        this.keySpec = new SecretKeySpec(
                digest.digest(secret.getBytes(StandardCharsets.UTF_8)), "AES");
    }

    /** Nonce acak baru untuk tiap panggilan. Jangan pernah dipakai ulang. */
    public byte[] newNonce() {
        byte[] nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(nonce);
        return nonce;
    }

    public String encode(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    public String encrypt(String plain, byte[] nonce) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BIT, nonce));
            return encode(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Gagal mengenkripsi API key", e);
        }
    }

    public String decrypt(String cipherTextBase64, String nonceBase64) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BIT,
                    Base64.getDecoder().decode(nonceBase64)));
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(cipherTextBase64));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Pesan sengaja tidak memuat detail teknis — yang gagal di sini bisa
            // berarti ciphertext diubah orang, dan itu bukan informasi yang
            // pantas dikembalikan ke pemanggil.
            throw new IllegalStateException("Gagal mendekripsi API key", e);
        }
    }
}
