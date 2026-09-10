package randomTeamFC27.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import randomTeamFC27.entity.RefreshToken;
import randomTeamFC27.entity.User;
import randomTeamFC27.exception.ApiException;
import randomTeamFC27.repository.RefreshTokenRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository);
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenTtlDays", 30L);
    }

    private User user(long id) {
        return User.builder().id(id).displayName("Emre").email("e@example.com").build();
    }

    @Test
    void issueSavesHashedTokenAndReturnsRawToken() {
        String raw = refreshTokenService.issue(user(1L), "test-device");

        assertNotNull(raw);
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertNotEquals(raw, captor.getValue().getTokenHash());
        assertEquals("test-device", captor.getValue().getDeviceInfo());
    }

    @Test
    void rotateRevokesOldTokenAndIssuesNew() {
        User user = user(1L);
        RefreshToken existing = RefreshToken.builder()
                .user(user).tokenHash("irrelevant-in-test")
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(existing));

        RefreshTokenService.RotationResult result = refreshTokenService.rotate("raw-token", "device");

        assertTrue(existing.isRevoked());
        assertNotNull(existing.getRevokedAt());
        assertEquals(user, result.user());
        assertNotNull(result.rawToken());
        verify(refreshTokenRepository, times(2)).save(any());
    }

    @Test
    void rotateRejectsRevokedToken() {
        RefreshToken revoked = RefreshToken.builder()
                .user(user(1L)).tokenHash("h")
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .revoked(true)
                .build();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(revoked));

        assertThrows(ApiException.class, () -> refreshTokenService.rotate("raw-token", "device"));
    }

    @Test
    void rotateRejectsExpiredToken() {
        RefreshToken expired = RefreshToken.builder()
                .user(user(1L)).tokenHash("h")
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expired));

        assertThrows(ApiException.class, () -> refreshTokenService.rotate("raw-token", "device"));
    }

    @Test
    void rotateRejectsUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> refreshTokenService.rotate("raw-token", "device"));
    }

    @Test
    void revokeSucceedsWhenTokenBelongsToCaller() {
        User owner = user(1L);
        RefreshToken token = RefreshToken.builder()
                .user(owner).tokenHash("h")
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        refreshTokenService.revoke(owner, "raw-token");

        assertTrue(token.isRevoked());
    }

    @Test
    void revokeRejectsTokenBelongingToSomeoneElse() {
        RefreshToken token = RefreshToken.builder()
                .user(user(1L)).tokenHash("h")
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        User attacker = user(2L);
        assertThrows(ApiException.class, () -> refreshTokenService.revoke(attacker, "raw-token"));
    }
}
