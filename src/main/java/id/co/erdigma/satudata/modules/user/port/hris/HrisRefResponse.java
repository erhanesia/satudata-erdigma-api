package id.co.erdigma.satudata.modules.user.port.hris;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

/**
 * Potongan halaman Spring yang dikembalikan hris-api untuk daftar rujukan.
 *
 * Bentuk `/position` dan `/employee` cukup mirip sehingga satu kelas melayani
 * keduanya: yang dibutuhkan pemilih hanya id dan nama. Balasan aslinya jauh
 * lebih besar — untuk karyawan ada jabatan, gedung, atasan, dan foto — dan
 * memetakan seluruhnya berarti mengikat portal ini pada bentuk internal HRIS.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HrisRefResponse {

    private List<Item> content;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private UUID id;
        private String name;
    }
}
