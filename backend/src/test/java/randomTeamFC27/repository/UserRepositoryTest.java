package randomTeamFC27.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import randomTeamFC27.entity.Role;
import randomTeamFC27.entity.User;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesUserAndAssignsAuditTimestamps() {
        User saved = userRepository.save(User.builder()
                .displayName("Emre")
                .email("emre@example.com")
                .role(Role.USER)
                .build());

        assertTrue(saved.getId() > 0);
        assertEquals("USER", saved.getRole().name());
        assertTrue(saved.isActive());
        assertEquals(saved.getCreatedAt(), saved.getUpdatedAt());
    }

    @Test
    void findsUserByEmail() {
        userRepository.save(User.builder()
                .displayName("Kaya")
                .email("kaya@example.com")
                .build());

        Optional<User> found = userRepository.findByEmail("kaya@example.com");

        assertTrue(found.isPresent());
        assertEquals("Kaya", found.get().getDisplayName());
    }

    @Test
    void findsUserByGoogleId() {
        userRepository.save(User.builder()
                .displayName("Deniz")
                .googleId("google-123")
                .build());

        assertTrue(userRepository.findByGoogleId("google-123").isPresent());
        assertFalse(userRepository.findByGoogleId("google-does-not-exist").isPresent());
    }
}
