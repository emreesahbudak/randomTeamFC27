package randomTeamFC27.security;

import org.junit.jupiter.api.Test;
import randomTeamFC27.entity.Role;
import randomTeamFC27.entity.User;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "test-only-secret-key-not-for-any-real-use-minimum-32-bytes", 15);

    private User user(long id, Role role) {
        return User.builder().id(id).displayName("Emre").email("e@example.com").role(role).build();
    }

    @Test
    void roundTripsUserIdAndRole() {
        String token = jwtService.generateAccessToken(user(42L, Role.ADMIN));

        Optional<JwtService.AccessTokenClaims> claims = jwtService.parse(token);

        assertTrue(claims.isPresent());
        assertEquals(42L, claims.get().userId());
        assertEquals(Role.ADMIN, claims.get().role());
    }

    @Test
    void rejectsMalformedToken() {
        assertTrue(jwtService.parse("not-a-jwt-at-all").isEmpty());
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtService otherService = new JwtService(
                "a-completely-different-secret-key-also-32-bytes!!", 15);
        String token = otherService.generateAccessToken(user(1L, Role.USER));

        assertTrue(jwtService.parse(token).isEmpty());
    }
}
