package randomTeamFC27.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import randomTeamFC27.entity.Team;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findAllByActiveTrue();

    List<Team> findAllByActiveTrueAndStarLevelInAndLeagueTypeId(List<Integer> starLevels, Long leagueTypeId);

    List<Team> findAllByActiveTrueAndStarLevelIn(List<Integer> starLevels);

    List<Team> findAllByActiveTrueAndLeagueTypeId(Long leagueTypeId);

    // Unfiltered-by-active counterparts, used only when includeInactive=true (admin
    // management view) — findAll() (inherited, no filters) covers the no-filter case.
    List<Team> findAllByStarLevelInAndLeagueTypeId(List<Integer> starLevels, Long leagueTypeId);

    List<Team> findAllByStarLevelIn(List<Integer> starLevels);

    List<Team> findAllByLeagueTypeId(Long leagueTypeId);
}
