package id.co.erdigma.satudata.modules.dataset.helper;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

/**
 * Mengubah judul menjadi slug yang aman dipakai di URL.
 *
 * Slug yang dihasilkan di sini hanyalah <em>usulan</em>. Penerbit tetap boleh
 * menggantinya sebelum dataset terbit, karena judul yang baik itu deskriptif
 * sementara slug yang baik itu pendek — dua tujuan yang sering bertabrakan.
 * Contoh nyata di katalog ini: "Utilisasi &amp; Kapasitas Tim" berslug
 * {@code utilisasi-sdm}, kata yang tidak muncul sama sekali di judulnya.
 */
@Component
public class SlugGenerator {

    private static final int MAX_LENGTH = 60;

    /**
     * Kata yang bentrok dengan rute atau punya arti khusus di URL. Dipakai
     * sebagai slug, semuanya akan menghasilkan tautan yang menunjuk ke tempat
     * yang salah — atau ke halaman yang belum tentu ada.
     */
    private static final Set<String> RESERVED = Set.of(
            "new", "create", "edit", "delete", "upload", "search",
            "api", "admin", "login", "logout", "me", "null", "undefined");

    /**
     * {@code "Penjualan Furnitur Ritel 2025"} → {@code "penjualan-furnitur-ritel-2025"}
     *
     * Huruf beraksen diuraikan lebih dulu (é → e) supaya tidak ikut terbuang
     * begitu saja; membuang diam-diam bisa mengubah dua judul berbeda menjadi
     * slug yang sama.
     */
    public String slugify(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String result = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        if (result.length() > MAX_LENGTH) {
            result = result.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return result;
    }

    /**
     * Menghasilkan slug yang benar-benar bisa dipakai: sudah dibersihkan, bukan
     * kata terlarang, dan belum dipakai dataset lain.
     *
     * @param sudahDipakai penentu ketersediaan. <strong>Wajib ikut memeriksa
     *                     baris yang sudah di-soft delete</strong> — kolom
     *                     {@code slug} punya UNIQUE constraint yang tidak peduli
     *                     pada {@code deleted_at}, jadi memeriksa hanya baris
     *                     hidup akan lolos di sini lalu gagal di database.
     */
    public String uniqueSlug(String usulan, Predicate<String> sudahDipakai) {
        String dasar = slugify(usulan);
        if (dasar.isEmpty()) {
            dasar = "dataset";
        }
        if (RESERVED.contains(dasar)) {
            dasar = dasar + "-dataset";
        }
        if (!sudahDipakai.test(dasar)) {
            return dasar;
        }
        // Berhenti di 999 supaya tidak ada kemungkinan berputar tanpa akhir
        // kalau predikatnya keliru dan selalu menjawab "sudah dipakai".
        for (int i = 2; i <= 999; i++) {
            String kandidat = dasar + "-" + i;
            if (!sudahDipakai.test(kandidat)) {
                return kandidat;
            }
        }
        throw new IllegalStateException("Tidak menemukan slug yang tersedia untuk: " + usulan);
    }

    /** Untuk slug yang diketik sendiri oleh penerbit. */
    public boolean isValid(String slug) {
        return slug != null
                && !slug.isBlank()
                && slug.length() <= MAX_LENGTH
                && slug.matches("[a-z0-9]+(-[a-z0-9]+)*")
                && !RESERVED.contains(slug);
    }
}
