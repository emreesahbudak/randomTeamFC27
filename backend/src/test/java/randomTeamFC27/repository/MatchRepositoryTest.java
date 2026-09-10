package randomTeamFC27.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import randomTeamFC27.entity.League;
import randomTeamFC27.entity.LeagueType;
import randomTeamFC27.entity.Match;
import randomTeamFC27.entity.Player;
import randomTeamFC27.entity.Season;
import randomTeamFC27.entity.Team;
import randomTeamFC27.entity.User;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class MatchRepositoryTest {

    @Autowired
    private MatchRepository matchRepository;
    @Autowired
    private LeagueRepository leagueRepository;
    @Autowired
    private SeasonRepository seasonRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private LeagueTypeRepository leagueTypeRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void listsMatchesForSeasonNewestFirst() {
        User owner = userRepository.save(User.builder().displayName("Emre").email("e@example.com").build());
        League league = leagueRepository.save(League.builder().name("Friday Nights").owner(owner).build());
        Season season = seasonRepository.save(Season.builder().league(league).name("Sezon 1").startedAt(Instant.now()).build());
        Player p1 = playerRepository.save(Player.builder().league(league).displayName("Ahmet").build());
        Player p2 = playerRepository.save(Player.builder().league(league).displayName("Mehmet").build());
        LeagueType emberCircuit = leagueTypeRepository.save(LeagueType.builder().name("Ember Circuit").build());
        LeagueType vantaLeague = leagueTypeRepository.save(LeagueType.builder().name("Vanta League").build());
        Team t1 = teamRepository.save(Team.builder().code("SOL").name("Solar FC").starLevel(5).leagueType(emberCircuit).build());
        Team t2 = teamRepository.save(Team.builder().code("VAN").name("Vanta Wolves").starLevel(5).leagueType(vantaLeague).build());

        matchRepository.save(Match.builder()
                .league(league).season(season).player1(p1).player2(p2).team1(t1).team2(t2)
                .score1(2).score2(1).recordedAt(Instant.now().minus(1, ChronoUnit.DAYS)).build());
        Match latest = matchRepository.save(Match.builder()
                .league(league).season(season).player1(p2).player2(p1).team1(t2).team2(t1)
                .score1(0).score2(3).recordedAt(Instant.now()).build());

        List<Match> matches = matchRepository.findAllBySeasonOrderByRecordedAtDesc(season);

        assertEquals(2, matches.size());
        assertEquals(latest.getId(), matches.get(0).getId());
    }
}
