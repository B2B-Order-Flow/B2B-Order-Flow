package com.b2borderflow.core.security;

import com.b2borderflow.core.config.AppSecurityProperties;
import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.user.AppUser;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Выдача JWT. Токен содержит {@code sub}, {@code login}, {@code roles}, {@code companyId}
 * и время жизни; решение о доступе всё равно принимается по БД (api-contract.md, 4.1).
 */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final AppSecurityProperties properties;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, AppSecurityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedToken issue(AppUser user, Set<Role> roles, UUID companyId) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.getJwtTtl());

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("login", user.getLogin())
                .claim("roles", roles.stream().map(Role::name).sorted().toList());

        if (companyId != null) {
            claims.claim("companyId", companyId.toString());
        }

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(token, properties.getJwtTtl().toSeconds());
    }

    /** Результат входа: сам токен и срок жизни в секундах. */
    public record IssuedToken(String accessToken, long expiresIn) {
    }
}
