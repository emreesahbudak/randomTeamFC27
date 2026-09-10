package randomTeamFC27.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import randomTeamFC27.entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    Optional<User> findByGoogleId(String googleId);
}
