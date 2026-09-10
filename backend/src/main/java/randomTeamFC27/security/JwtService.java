package randomTeamFC27.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import randomTeamFC27.entity.Role;
import randomTeamFC27.entity.User;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and validates short-lived access tokens. Refresh tokens are a separate,
 * opaque, DB-backed concept handled by {@link randomTeamFC27.service.RefreshTokenService} —
 * this class only ever deals with the stateless JWT access token.
 */
@Component
public class JwtService {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final Duration accessTokenTtl;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-ttl-minutes}") long accessTokenTtlMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMinutes(accessTokenTtlMinutes);
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(ROLE_CLAIM, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    /**
     * @return the parsed claims if {@code token} is a well-formed, unexpired, correctly
     * signed access token; empty otherwise (never throws for caller convenience).
     */
    public java.util.Optional<AccessTokenClaims> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            Role role = Role.valueOf(claims.get(ROLE_CLAIM, String.class));
            return java.util.Optional.of(new AccessTokenClaims(userId, role));
        } catch (JwtException | IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }

    public record AccessTokenClaims(Long userId, Role role) {
    }
}
