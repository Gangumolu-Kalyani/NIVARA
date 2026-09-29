package com.sih.nivara.security;

import com.sih.nivara.entity.AppUser;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Issues signed access tokens for authenticated accounts.
 *
 * <p>The subject is the account uuid: never the bigint id, which stays inside the backend, and
 * never the email, which can change. The role claim describes the account for clients; the
 * server itself takes the role from the database on every request.
 */
@Service
public class JwtTokenService {

    /** The iss claim of every token NIVARA issues, and the only one it accepts. */
    public static final String ISSUER = "nivara";

    /**
     * Names the paired device a PATIENT token was issued to. Such tokens are accepted only while
     * that device is still active; see AccountJwtAuthenticationConverter.
     */
    public static final String DEVICE_CLAIM = "device";

    /** A freshly signed token and the instant it stops being accepted. */
    public record IssuedToken(String value, Instant expiresAt) {
    }

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public IssuedToken issue(AppUser account) {
        return issue(account, null);
    }

    /** A token for a patient's account, bound to the paired device it was issued to. */
    public IssuedToken issueForDevice(AppUser patientAccount, UUID deviceUuid) {
        return issue(patientAccount, deviceUuid);
    }

    private IssuedToken issue(AppUser account, UUID deviceUuid) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.ttl());
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(account.getUuid().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("role", account.getRole().name());
        if (deviceUuid != null) {
            builder.claim(DEVICE_CLAIM, deviceUuid.toString());
        }
        JwtClaimsSet claims = builder.build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(value, expiresAt);
    }
}
