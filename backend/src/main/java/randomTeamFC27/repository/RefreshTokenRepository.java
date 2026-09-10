package randomTeamFC27.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import randomTeamFC27.entity.RefreshToken;
import randomTeamFC27.entity.User;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUserAndRevokedFalse(User user);

    void deleteAllByUser(User user);
}
