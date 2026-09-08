package id.co.erdigma.satudata.enums;

import java.util.UUID;

import id.co.erdigma.satudata.exception.BusinessValidationException;

/**
 * Awalan yang ditempelkan di depan UUID saat ditampilkan di API.
 *
 * Tujuannya membuat identitas bisa dikenali begitu terlihat: sebuah id yang
 * muncul di log, pesan galat, atau tiket dukungan langsung memberi tahu ia milik
 * tabel mana, tanpa perlu menelusuri database.
 *
 * Ini murni lapisan tampilan. Kolom di database tetap bertipe UUID dan tidak
 * pernah menyimpan awalannya — jadi indeks, foreign key, dan ukuran baris tidak
 * berubah sama sekali.
 *
 * Awalan sengaja pendek dan tidak ada yang sama, supaya tidak ada dua tabel yang
 * bisa tertukar saat dibaca sekilas.
 */
public enum IdPrefix {

    DATASET("dst"),
    DATASET_COLUMN("dcol"),
    DATASET_RESOURCE("dres"),
    DIVISION("div"),
    COLLECTION("col"),
    TOPIC("tpc"),
    FORMAT("fmt"),
    USER("usr"),
    INCIDENT("inc");

    private final String value;

    IdPrefix(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /** {@code dst-e0000000-0000-4000-8000-000000000009} */
    public String format(UUID id) {
        return id == null ? null : value + "-" + id;
    }

    /**
     * Kebalikan {@link #format(UUID)}, dan sengaja memaafkan dua hal.
     *
     * UUID telanjang tanpa awalan tetap diterima: klien lama, tautan yang
     * terlanjur tersimpan, dan skrip yang ditulis sebelum awalan ada tidak perlu
     * ikut rusak karena perubahan tampilan.
     *
     * Awalan milik tabel lain ditolak dengan pesan yang menyebutkan bentuk yang
     * benar — mengirim id divisi ke endpoint dataset lebih mungkin berarti salah
     * salin daripada serangan, dan pesan yang jelas menghemat waktu.
     */
    public UUID parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessValidationException("Id tidak boleh kosong.");
        }
        String cleaned = raw.trim();
        String awalan = value + "-";

        if (cleaned.startsWith(awalan)) {
            cleaned = cleaned.substring(awalan.length());
        } else if (cleaned.indexOf('-') > 0 && !isUuidShaped(cleaned)) {
            throw new BusinessValidationException(
                    "Id \"" + raw + "\" bukan milik sumber daya ini. Bentuk yang benar diawali \""
                            + awalan + "\".");
        }

        try {
            return UUID.fromString(cleaned);
        } catch (IllegalArgumentException e) {
            throw new BusinessValidationException("Id \"" + raw + "\" bukan UUID yang sah.");
        }
    }

    /**
     * UUID punya 4 tanda hubung dan panjang 36. Dipakai untuk membedakan UUID
     * telanjang dari string berawalan — keduanya sama-sama memuat tanda hubung,
     * jadi keberadaan tanda hubung saja tidak cukup jadi penanda.
     */
    private static boolean isUuidShaped(String value) {
        return value.length() == 36 && value.chars().filter(c -> c == '-').count() == 4;
    }
}
