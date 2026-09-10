package randomTeamFC27.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import randomTeamFC27.entity.Match;
import randomTeamFC27.entity.Season;

import java.util.List;

public interface MatchRepository extends JpaRepository<Match, Long> {

    List<Match> findAllBySeasonOrderByRecordedAtDesc(Season season);
}
