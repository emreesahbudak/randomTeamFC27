package randomTeamFC27.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import randomTeamFC27.entity.League;
import randomTeamFC27.entity.Season;

import java.util.List;
import java.util.Optional;

public interface SeasonRepository extends JpaRepository<Season, Long> {

    Optional<Season> findFirstByLeagueAndEndedAtIsNull(League league);

    List<Season> findAllByLeagueAndEndedAtIsNotNullOrderByEndedAtDesc(League league);
}
