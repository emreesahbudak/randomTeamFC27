package randomTeamFC27.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import randomTeamFC27.entity.League;
import randomTeamFC27.entity.Player;

import java.util.List;
import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findAllByLeague(League league);

    List<Player> findAllByLeagueAndActiveTrue(League league);

    Optional<Player> findByLeagueAndDisplayName(League league, String displayName);
}
