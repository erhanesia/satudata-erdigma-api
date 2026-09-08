package id.co.erdigma.satudata.modules.user.port.hris;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

/**
 * Potongan balasan GET /api/v1/user/me milik hris-api yang benar-benar dipakai
 * Satu Data.
 *
 * Balasan aslinya jauh lebih besar — kebijakan izin, gedung, atasan, tribe,
 * tanggal lahir. Memetakan seluruhnya berarti mengikat portal ini pada bentuk
 * internal HRIS dan membuatnya ikut pecah setiap kali bentuk itu berubah.
 * ignoreUnknown = true membuat penambahan ruas di sisi HRIS tidak berakibat
 * apa-apa di sini.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HrisMeResponse {

    private String email;

    /** Enum Role milik hris-api, diterima sebagai teks supaya tidak perlu diimpor. */
    private String role;

    private Employee employee;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Employee {
        /**
         * Pengenal karyawan di HRIS, dipakai mencocokkan aturan akses bertipe
         * EMPLOYEE. Sudah selalu dikirim HRIS; sebelum changeset 48 hanya tidak
         * pernah dibaca.
         */
        private UUID id;
        private String name;
        private String jobLevel;
        /**
         * Letak foto di S3, mis. {@code /hris/dev/profile-image/230425-0808.webp}.
         * Sudah selalu dikirim HRIS; sebelum changeset 49 hanya tidak dibaca.
         */
        private String profileImage;
        private Position position;
        /**
         * Unit kerja orang ini, dipetakan ke {@code division} lewat
         * {@code division.hris_team_id}.
         *
         * Sebelum changeset 41 yang dibaca di sini {@code departement}, dan itu
         * keliru: `departement` di HRIS adalah badan usaha — Gemilang Multazam,
         * Erha Idea Cipta Karsa, dan empat lainnya — bukan unit kerja. `team`
         * yang berisi Data & IT, HRGA Team, Finance Accounting & Tax, dan
         * seterusnya.
         *
         * hris-api sebenarnya sudah mengirimkan ruas ini sejak dulu; yang
         * membuatnya tidak terpakai adalah kelas ini yang belum
         * mendeklarasikannya, sehingga {@code ignoreUnknown = true} membuangnya
         * diam-diam.
         */
        private Team team;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Position {
        /**
         * Dipakai mencocokkan aturan akses bertipe POSITION. Namanya ikut dibaca
         * untuk ditampilkan, tetapi yang dicocokkan id-nya — nama posisi di HRIS
         * memuat salah ketik yang suatu saat diperbaiki, dan pencocokan berbasis
         * nama akan putus diam-diam begitu itu terjadi.
         */
        private UUID id;
        private String name;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Team {
        private UUID id;
    }
}
