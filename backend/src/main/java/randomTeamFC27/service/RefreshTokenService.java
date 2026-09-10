package randomTeamFC27.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import randomTeamFC27.entity.RefreshToken;
import randomTeamFC27.entity.User;
import randomTeamFC27.exception.ApiException;
import randomTeamFC27.repository.RefreshTokenRepository;
import randomTeamFC27.security.HashUtil;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.jwt.refresh-token-ttl-days}")
    private long refreshTokenTtlDays;

    public String issue(User user, String deviceInfo) {
        String rawToken = generateRawToken();
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(HashUtil.sha256Hex(rawToken))
                .expiresAt(Instant.now().plus(Duration.ofDays(refreshTokenTtlDays)))
                .deviceInfo(deviceInfo)
                .build();
        refreshTokenRepository.save(token);
        log.info("Issued refresh token for user {}", user.getId());
        return rawToken;
    }

    /**
     * Validates {@code rawToken}, revokes it, and issues a fresh one for the same user
     * (rotation) — reusing a revoked/expired token is always rejected.
     */
    public RotationResult rotate(String rawToken, String deviceInfo) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(HashUtil.sha256Hex(rawToken))
                .orElseThrow(() -> ApiException.unauthorized("Geçersiz refresh token"));

        if (existing.isRevoked() || existing.getExpiresAt().isBefore(Instant.now())) {
            log.warn("Rejected refresh token for user {} (revoked={}, expired={})",
                    existing.getUser().getId(), existing.isRevoked(), existing.getExpiresAt().isBefore(Instant.now()));
            throw ApiException.unauthorized("Refresh token artık geçerli değil");
        }

        existing.setRevoked(true);
        existing.setRevokedAt(Instant.now());
        refreshTokenRepository.save(existing);

        String newRawToken = issue(existing.getUser(), deviceInfo);
        return new RotationResult(existing.getUser(), newRawToken);
    }

    /**
     * Revokes {@code rawToken} on behalf of {@code currentUser} — throws if the token
     * doesn't exist or doesn't belong to that user, so one session can't revoke another's.
     */
    public void revoke(User currentUser, String rawToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(HashUtil.sha256Hex(rawToken))
                .orElseThrow(() -> ApiException.unauthorized("Geçersiz refresh token"));

        if (!token.getUser().getId().equals(currentUser.getId())) {
            log.warn("User {} attempted to revoke a refresh token belonging to user {}",
                    currentUser.getId(), token.getUser().getId());
            throw ApiException.unauthorized("Geçersiz refresh token");
        }

        token.setRevoked(true);
        token.setRevokedAt(Instant.now());
        refreshTokenRepository.save(token);
        log.info("Revoked refresh token for user {}", currentUser.getId());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record RotationResult(User user, String rawToken) {
    }
}
