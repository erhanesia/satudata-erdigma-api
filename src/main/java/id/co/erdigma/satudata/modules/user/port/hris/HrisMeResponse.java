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
        private String name;
        private String jobLevel;
        private Position position;
        private Departement departement;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Position {
        private String name;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Departement {
        private UUID id;
    }
}
