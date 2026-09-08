package id.co.erdigma.satudata.modules.dataset.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import id.co.erdigma.satudata.enums.AccessRuleType;
import id.co.erdigma.satudata.enums.HrisJobLevel;
import id.co.erdigma.satudata.exception.BusinessValidationException;
import id.co.erdigma.satudata.modules.dataset.dto.AccessRuleDTO;
import id.co.erdigma.satudata.modules.dataset.entity.AccessRule;

/**
 * Memeriksa aturan akses yang dikirim penerbit, dan mengubahnya jadi bentuk yang
 * disimpan.
 *
 * Satu tempat, dipakai jalur unggah maupun jalur ubah. Menyalin pemeriksaannya
 * ke dua tempat berarti cepat atau lambat ada satu yang menerima nilai yang
 * ditolak yang lain, dan yang menerima itulah yang menciptakan baris aturan yang
 * tidak akan pernah cocok dengan siapa pun.
 */
@Component
public class AccessRuleValidator {

    /**
     * @throws BusinessValidationException bila ada aturan yang tidak sah
     */
    public List<AccessRule> validate(List<AccessRuleDTO> requested) {
        List<AccessRule> result = new ArrayList<>();
        if (requested == null || requested.isEmpty()) {
            return result;
        }

        for (AccessRuleDTO dto : requested) {
            // Aturan cacat DITOLAK, bukan dilewati.
            //
            // Sebelumnya keduanya dilewati diam-diam, dan itu gagal ke arah yang
            // salah. Daftar berisi satu aturan cacat berakhir sebagai daftar
            // kosong, dan daftar kosong berarti "terbuka untuk seluruh
            // karyawan" -- sehingga permintaan yang cacat MENGHAPUS seluruh
            // pembatasan sebuah dataset lalu menjawab 200.
            //
            // Daftar yang memang kosong tetap sah dan tetap berarti membuka;
            // itu ditangani lebih awal, sebelum perulangan ini. Yang ditolak di
            // sini hanya daftar berisi yang salah bentuk, karena pengirimnya
            // jelas bermaksud memasang aturan dan gagal.
            //
            // @Valid di controller sudah menahannya lebih dulu dengan 400 yang
            // lebih informatif. Pemeriksaan ini lapis kedua, untuk jalur yang
            // suatu saat memanggil validator ini tanpa lewat controller.
            if (dto == null || dto.getRuleType() == null) {
                throw new BusinessValidationException(
                        "Ada aturan tanpa jenis. Setiap aturan wajib menyebut ruleType.");
            }
            String value = (dto.getRuleValue() == null) ? null : dto.getRuleValue().trim();
            if (value == null || value.isEmpty()) {
                throw new BusinessValidationException(
                        "Aturan bertipe " + dto.getRuleType() + " dikirim tanpa nilai. "
                                + "Kirim daftar kosong kalau memang ingin membukanya untuk semua.");
            }

            AccessRule rule = new AccessRule(dto.getRuleType(), normalise(dto.getRuleType(), value));

            // Duplikat dibuang diam-diam, bukan ditolak. Mengirim posisi yang
            // sama dua kali adalah kecerobohan antarmuka, bukan kesalahan
            // penerbit, dan menolaknya hanya membuat ia menebak-nebak yang mana.
            if (!result.contains(rule)) {
                result.add(rule);
            }
        }
        return result;
    }

    private String normalise(AccessRuleType type, String value) {
        return switch (type) {
            case JOB_LEVEL -> {
                HrisJobLevel level = HrisJobLevel.fromLabel(value);
                if (level == null) {
                    throw new BusinessValidationException(
                            "Jenjang jabatan \"" + value + "\" tidak dikenal. "
                                    + "Lihat GET /api/v1/job-levels.");
                }
                // Disimpan dalam bentuk baku dari enum, bukan apa adanya dari
                // pengirim. "senior manager" dan "Senior Manager" harus berakhir
                // sebagai satu baris yang sama, bukan dua.
                yield level.getLabel();
            }
            case POSITION, EMPLOYEE -> {
                try {
                    // Ditulis ulang lewat UUID supaya bentuknya baku. Nilai yang
                    // sama bisa datang dengan huruf besar-kecil berbeda, dan dua
                    // baris yang sebenarnya sama akan lolos pemeriksaan duplikat.
                    yield UUID.fromString(value).toString();
                } catch (IllegalArgumentException e) {
                    throw new BusinessValidationException(
                            "Nilai aturan " + type + " harus berupa UUID dari HRIS, "
                                    + "bukan \"" + value + "\". Ambil dari "
                                    + (type == AccessRuleType.POSITION
                                            ? "GET /api/v1/positions."
                                            : "GET /api/v1/employees."));
                }
            }
        };
    }
}
