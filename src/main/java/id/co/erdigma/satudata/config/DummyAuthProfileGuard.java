package id.co.erdigma.satudata.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PostConstruct;

/**
 * Pengaman: profil auth-dummy TIDAK BOLEH menyala di produksi. Kalau keduanya
 * aktif bersamaan, aplikasi sengaja dimatikan saat startup — auth dummy yang
 * lolos ke produksi berarti portal berisi data rahasia terbuka bagi siapa pun.
 */
// Ekspresi "a & b", bukan {"a","b"} — bentuk array di @Profile bermakna ATAU,
// sedangkan yang dibutuhkan di sini adalah DAN.
@Configuration
@Profile("auth-dummy & prod")
public class DummyAuthProfileGuard {

    @PostConstruct
    public void verify() {
        throw new IllegalStateException(
                "Profil 'auth-dummy' aktif bersamaan dengan 'prod'. "
                        + "Autentikasi dummy dilarang di produksi — gunakan profil 'auth-hris'.");
    }
}
