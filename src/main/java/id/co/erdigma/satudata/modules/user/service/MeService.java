package id.co.erdigma.satudata.modules.user.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
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
        response.setAccessPosition(user.getAccessPosition());
        if (user.getDivision() != null) {
            response.setDivision(divisionMapper.toResponseLite(user.getDivision()));
        }
        return response;
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
