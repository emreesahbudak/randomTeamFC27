package randomTeamFC27.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import randomTeamFC27.entity.League;
import randomTeamFC27.entity.User;

import java.util.List;

public interface LeagueRepository extends JpaRepository<League, Long> {

    List<League> findAllByOwner(User owner);
}
