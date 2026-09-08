package id.co.erdigma.satudata.modules.user.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import id.co.erdigma.satudata.entity.User;
import id.co.erdigma.satudata.modules.division.mapper.DivisionMapper;
import id.co.erdigma.satudata.modules.user.dto.UserResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MeService {

    @Autowired
    private DivisionMapper divisionMapper;

    /**
     * Alamat dasar berkas milik HRIS, tanpa garis miring di ujung.
     *
     * Berkasnya ada di bucket yang sama dengan milik Satu Data, hanya beda
     * prefix, dan bucket itu bisa dibaca publik. Karena itu foto profil cukup
     * ditunjuk langsung alih-alih diteruskan lewat portal ini — meneruskannya
     * berarti setiap pemuatan halaman menyalurkan gambar lewat aplikasi tanpa
     * satu pun manfaat, karena tidak ada yang perlu diaudit dari melihat foto
     * diri sendiri.
     *
     * Kalau suatu saat bucket-nya ditutup dari publik, yang berubah cukup di
     * sini: nilainya diarahkan ke endpoint penerus, dan front-end tidak perlu
     * tahu apa-apa.
     */
    @Value("${satudata.hris.file-base-url:https://erhanesia-files.s3.ap-southeast-1.amazonaws.com}")
    private String hrisFileBaseUrl;

    public UserResponse toResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setPosition(user.getPosition());
        response.setInitials(buildInitials(user.getName()));
        response.setRole(user.getRole());
        response.setHrisPermissionLevel(user.getHrisPermissionLevel());
        response.setJobLevel(user.getJobLevel());
        response.setProfileImageUrl(buildProfileImageUrl(user.getProfileImage()));
        if (user.getDivision() != null) {
            response.setDivision(divisionMapper.toResponseLite(user.getDivision()));
        }
        return response;
    }

    /**
     * Menyusun URL utuh dari path yang dikirim HRIS.
     *
     * HRIS menyimpan path berawalan garis miring — {@code /hris/dev/…} — tetapi
     * itu tidak dijamin. Kedua bentuk dirapikan di sini supaya hasilnya tidak
     * pernah punya garis miring ganda, yang pada beberapa penyaji S3 dianggap
     * kunci yang berbeda dan berujung 404.
     */
    private String buildProfileImageUrl(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String cleaned = path.trim();

        // Sudah berupa URL utuh: dipakai apa adanya. Bentuk ini belum pernah
        // muncul dari HRIS, tetapi kalau suatu saat muncul, menempelkan alamat
        // dasar di depannya justru menghasilkan tautan yang pasti rusak.
        if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
            return cleaned;
        }

        String base = hrisFileBaseUrl.endsWith("/")
                ? hrisFileBaseUrl.substring(0, hrisFileBaseUrl.length() - 1)
                : hrisFileBaseUrl;
        return cleaned.startsWith("/") ? base + cleaned : base + "/" + cleaned;
    }

    /**
     * "M. Fahrega Ridwan" -> "FR", mengikuti avatar di desain.
     *
     * Bagian yang hanya satu huruf dianggap inisial (mis. "M.") dan dilewati,
     * kecuali kalau setelah disaring tidak tersisa apa pun.
     */
    private String buildInitials(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        List<String> words = Arrays.stream(name.trim().split("\\s+"))
                .map(part -> part.replaceAll("[^A-Za-z]", ""))
                .filter(part -> !part.isEmpty())
                .toList();

        List<String> meaningful = words.stream().filter(part -> part.length() > 1).toList();
        List<String> source = meaningful.isEmpty() ? words : meaningful;

        StringBuilder initials = new StringBuilder();
        for (String part : source) {
            initials.append(Character.toUpperCase(part.charAt(0)));
            if (initials.length() == 2) {
                break;
            }
        }
        return initials.length() == 0 ? "?" : initials.toString();
    }
}
