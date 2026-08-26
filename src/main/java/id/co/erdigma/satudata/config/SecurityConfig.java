package id.co.erdigma.satudata.config;

import java.util.Arrays;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

        private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
        private final CustomJwtAuthenticationConverter customConverter;

        public SecurityConfig(
                        CustomAuthenticationEntryPoint customAuthenticationEntryPoint,
                        CustomJwtAuthenticationConverter customConverter) {
                this.customAuthenticationEntryPoint = customAuthenticationEntryPoint;
                this.customConverter = customConverter;
        }

        public static final String[] PUBLIC_GET_ENDPOINTS = {
                        "/api/v1/public/**",
                        "/health/**",
                        "/actuator/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/swagger-resources/**",
                        "/webjars/**",
                        "/configuration/ui",
                        "/configuration/security"
        };

        // @Order(1) sengaja dikosongkan — jalur mesin (API key, rate limit) akan
        // menempati slot itu tanpa perlu menomori ulang chain di bawah.

        @Bean
        @Order(2)
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                .securityMatcher("/**")
                                .csrf(csrf -> csrf.disable())
                                .cors(Customizer.withDefaults())
                                .authorizeHttpRequests(auth -> auth
                                                // Galat yang tidak tertangani diteruskan Spring ke /error lewat
                                                // dispatch ERROR. Dispatch itu masuk ulang ke rantai keamanan tanpa
                                                // membawa SecurityContext. Tanpa baris ini setiap galat menyamar
                                                // jadi 401 dan pesan aslinya hilang. Yang diizinkan adalah jenis
                                                // dispatch-nya, bukan URL /error, jadi permintaan dari luar ke
                                                // /error tetap dijaga seperti biasa.
                                                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                                                .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                                                .anyRequest().authenticated())
                                .exceptionHandling(ex -> ex.authenticationEntryPoint(customAuthenticationEntryPoint))
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .requestCache(requestCache -> requestCache.requestCache(new NullRequestCache()));

                http.oauth2ResourceServer(oauth -> oauth
                                .jwt(jwt -> jwt.jwtAuthenticationConverter(customConverter)));

                return http.build();
        }

        /**
         * Bawaan mengikuti Vite — dipakai kedua front-end perusahaan (HRIS-WEB dan
         * Taskfy): 5173 dev, 5174 dev cadangan, 4173 preview build. Host produksi
         * ditimpa lewat properti satudata.cors.allowed-origins saat deploy,
         * jangan ditambahkan di sini.
         */
        @Value("${satudata.cors.allowed-origins:http://localhost:5173,http://localhost:5174,http://localhost:4173}")
        private String[] allowedOrigins;

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOrigins(Arrays.asList(allowedOrigins));
                configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
                configuration.setAllowedHeaders(
                                Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
                configuration.setAllowCredentials(true);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }
}
