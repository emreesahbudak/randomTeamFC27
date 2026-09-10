package randomTeamFC27.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import randomTeamFC27.entity.LeagueType;
import randomTeamFC27.entity.Team;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class TeamRepositoryTest {

    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private LeagueTypeRepository leagueTypeRepository;

    private LeagueType leagueType(String name) {
        return leagueTypeRepository.save(LeagueType.builder().name(name).build());
    }

    @Test
    void filtersActiveTeamsByStarLevelsAndLeagueType() {
        LeagueType emberCircuit = leagueType("Ember Circuit");
        LeagueType vantaLeague = leagueType("Vanta League");

        teamRepository.save(Team.builder().code("SOL").name("Solar FC").colorHex("#e8b34d")
                .starLevel(5).leagueType(emberCircuit).active(true).build());
        teamRepository.save(Team.builder().code("CIN").name("Cinderpark Athletic").colorHex("#b5432e")
                .starLevel(2).leagueType(emberCircuit).active(true).build());
        teamRepository.save(Team.builder().code("VAN").name("Vanta Wolves").colorHex("#3b3f6b")
                .starLevel(5).leagueType(vantaLeague).active(true).build());
        teamRepository.save(Team.builder().code("OLD").name("Retired FC").colorHex("#000000")
                .starLevel(5).leagueType(emberCircuit).active(false).build());

        List<Team> result = teamRepository.findAllByActiveTrueAndStarLevelInAndLeagueTypeId(
                List.of(4, 5), emberCircuit.getId());

        assertEquals(1, result.size());
        assertEquals("SOL", result.get(0).getCode());
    }

    @Test
    void findsOnlyActiveTeams() {
        LeagueType leagueType = leagueType("X League");
        teamRepository.save(Team.builder().code("A").name("A FC").starLevel(3).leagueType(leagueType).active(true).build());
        teamRepository.save(Team.builder().code("B").name("B FC").starLevel(3).leagueType(leagueType).active(false).build());

        List<Team> active = teamRepository.findAllByActiveTrue();

        assertEquals(1, active.size());
        assertTrue(active.stream().allMatch(Team::isActive));
    }
}
