package com.sih.nivara.security;

import com.sih.nivara.entity.enums.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * HTTP security: stateless bearer-token authentication for the whole API.
 *
 * <p>Only three endpoints are public: the health check, registration and login. Every other
 * request needs {@code Authorization: Bearer <jwt>} and is otherwise answered 401. Tokens are
 * HS256 JWTs signed and verified with the JWT_SECRET key through Spring's own resource-server
 * support, so there is no session, no cookie and therefore no CSRF surface.
 *
 * <p>Roles decide which part of the API a caller may use: PATIENT accounts, signed in on a paired
 * device, reach only /api/me/** and their own /api/auth/me; the caregiver API is for CAREGIVER
 * and ADMIN accounts. Which patients a caregiver may reach is then decided per request by
 * PatientAccessService, from patient_caregivers.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            AccountJwtAuthenticationConverter accountConverter) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        // A patient's device signs in with a pairing code or its device secret.
                        .requestMatchers(HttpMethod.POST, "/api/auth/device/pair", "/api/auth/device/token").permitAll()
                        // Spring Boot renders error responses on /error; without this, a 400 or 409
                        // raised by a public endpoint would reach the client as 401.
                        .requestMatchers("/error").permitAll()
                        // Every account may read its own account.
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        // A patient's own device: only PATIENT accounts, for their own record.
                        .requestMatchers("/api/me/**").hasRole(UserRole.PATIENT.name())
                        // Everything else is the caregiver API. A PATIENT token is refused here
                        // (403), whatever PatientAccessService would decide.
                        .requestMatchers("/api/**").hasAnyRole(UserRole.CAREGIVER.name(), UserRole.ADMIN.name())
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(accountConverter))
                        // Spring Security 7 always publishes RFC 9728 metadata at
                        // /.well-known/oauth-protected-resource. Its default claims that tokens
                        // are bound to TLS client certificates, which is false here.
                        .protectedResourceMetadata(metadata -> metadata.protectedResourceMetadataCustomizer(
                                builder -> builder.tlsClientCertificateBoundAccessTokens(false))));
        return http.build();
    }

    /**
     * Lets the browser frontend call the API from its own origin (the Vite dev server by default).
     * Only the origins listed in nivara.cors.allowed-origins are accepted. No cookies are involved,
     * since tokens travel in the Authorization header, so credentials stay disallowed.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${nivara.cors.allowed-origins:http://localhost:5173}") List<String> allowedOrigins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        cors.setExposedHeaders(List.of("Location"));
        cors.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    /** BCrypt through the delegating encoder, so stored hashes carry their {bcrypt} algorithm id. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecretKey jwtSigningKey(JwtProperties properties) {
        return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSigningKey).algorithm(MacAlgorithm.HS256).build();
    }

    /**
     * Accepts only HS256 tokens signed with our key and issued by NIVARA, and checks their expiry.
     * Any other algorithm, including the unsigned "none", is rejected.
     */
    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JwtTokenService.ISSUER));
        return decoder;
    }
}
