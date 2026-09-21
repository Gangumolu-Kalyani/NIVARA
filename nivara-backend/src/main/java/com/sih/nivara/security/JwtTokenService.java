package com.sih.nivara.security;

import com.sih.nivara.entity.AppUser;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

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
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(account.getUuid().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("role", account.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(value, expiresAt);
    }
}
