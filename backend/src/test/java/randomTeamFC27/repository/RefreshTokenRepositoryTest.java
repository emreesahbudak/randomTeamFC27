package randomTeamFC27.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import randomTeamFC27.entity.RefreshToken;
import randomTeamFC27.entity.User;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findsTokenByHash() {
        User user = userRepository.save(User.builder().displayName("Emre").email("e@example.com").build());
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash("hash-abc")
                .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .build());

        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash("hash-abc");

        assertTrue(found.isPresent());
        assertEquals(user.getId(), found.get().getUser().getId());
    }

    @Test
    void findsOnlyActiveTokensForUser() {
        User user = userRepository.save(User.builder().displayName("Kaya").email("k@example.com").build());
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user).tokenHash("active-1").expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .revoked(false).build());
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user).tokenHash("revoked-1").expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .revoked(true).revokedAt(Instant.now()).build());

        List<RefreshToken> active = refreshTokenRepository.findAllByUserAndRevokedFalse(user);

        assertEquals(1, active.size());
        assertEquals("active-1", active.get(0).getTokenHash());
    }

    @Test
    void deletesAllTokensForUser() {
        User user = userRepository.save(User.builder().displayName("Deniz").email("d@example.com").build());
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user).tokenHash("t1").expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).build());
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user).tokenHash("t2").expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).build());

        refreshTokenRepository.deleteAllByUser(user);

        assertTrue(refreshTokenRepository.findAllByUserAndRevokedFalse(user).isEmpty());
    }
}
