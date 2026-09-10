package randomTeamFC27.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import randomTeamFC27.dto.league.MatchCreateRequest;
import randomTeamFC27.dto.league.MatchResponse;
import randomTeamFC27.entity.League;
import randomTeamFC27.entity.LeagueType;
import randomTeamFC27.entity.Player;
import randomTeamFC27.entity.Role;
import randomTeamFC27.entity.Season;
import randomTeamFC27.entity.Team;
import randomTeamFC27.entity.User;
import randomTeamFC27.exception.ApiException;
import randomTeamFC27.repository.MatchRepository;
import randomTeamFC27.repository.PlayerRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock
    private MatchRepository matchRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private LeagueService leagueService;
    @Mock
    private SeasonService seasonService;
    @Mock
    private TeamService teamService;

    private MatchService matchService;

    @BeforeEach
    void setUp() {
        matchService = new MatchService(matchRepository, playerRepository, leagueService, seasonService, teamService);
    }

    private User owner() {
        return User.builder().id(1L).displayName("Owner").role(Role.USER).build();
    }

    private League league() {
        return League.builder().id(1L).name("Friday Nights").owner(owner()).build();
    }

    private Season season(League league) {
        return Season.builder().id(1L).league(league).name("Sezon 1").startedAt(Instant.now()).build();
    }

    private Player player(long id, League league) {
        return Player.builder().id(id).league(league).displayName("P" + id).active(true).build();
    }

    private Team team(long id) {
        return Team.builder().id(id).name("Team " + id).code("T" + id).starLevel(3)
                .leagueType(LeagueType.builder().id(1L).name("Ember Circuit").build()).active(true).build();
    }

    @Test
    void rejectsPlayerMatchedAgainstThemselves() {
        League league = league();
        when(leagueService.getOwned(1L, owner())).thenReturn(league);
        lenient().when(seasonService.currentSeason(league)).thenReturn(season(league));
        MatchCreateRequest request = new MatchCreateRequest(5L, 5L, 10L, 20L, 1, 1);

        assertThrows(ApiException.class, () -> matchService.record(1L, request, owner()));
    }

    @Test
    void rejectsPlayerNotInThisLeague() {
        League league = league();
        League otherLeague = League.builder().id(2L).name("Other").owner(owner()).build();
        when(leagueService.getOwned(1L, owner())).thenReturn(league);
        lenient().when(seasonService.currentSeason(league)).thenReturn(season(league));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player(5L, otherLeague)));
        MatchCreateRequest request = new MatchCreateRequest(5L, 6L, 10L, 20L, 1, 1);

        assertThrows(ApiException.class, () -> matchService.record(1L, request, owner()));
    }

    @Test
    void recordSavesMatchAndResolvesPlayerNamesForResponse() {
        League league = league();
        Season season = season(league);
        Player p1 = player(5L, league);
        Player p2 = player(6L, league);
        Team t1 = team(10L);
        Team t2 = team(20L);
        when(leagueService.getOwned(1L, owner())).thenReturn(league);
        when(seasonService.currentSeason(league)).thenReturn(season);
        when(playerRepository.findById(5L)).thenReturn(Optional.of(p1));
        when(playerRepository.findById(6L)).thenReturn(Optional.of(p2));
        when(teamService.get(10L)).thenReturn(t1);
        when(teamService.get(20L)).thenReturn(t2);
        when(matchRepository.save(any())).thenAnswer(inv -> {
            var match = (randomTeamFC27.entity.Match) inv.getArgument(0);
            match.setId(100L);
            return match;
        });

        MatchResponse response = matchService.record(1L, new MatchCreateRequest(5L, 6L, 10L, 20L, 3, 1), owner());

        assertEquals("P5", response.player1Name());
        assertEquals("P6", response.player2Name());
        assertEquals(3, response.score1());
        assertEquals(1, response.score2());
    }

    @Test
    void listResolvesNamesFromLeagueRosterNotLazyRelations() {
        League league = league();
        Season season = season(league);
        Player p1 = player(5L, league);
        Player p2 = player(6L, league);
        randomTeamFC27.entity.Match match = randomTeamFC27.entity.Match.builder()
                .id(100L).league(league).season(season).player1(p1).player2(p2)
                .team1(team(10L)).team2(team(20L)).score1(2).score2(0)
                .recordedAt(Instant.now()).build();
        when(leagueService.getOwned(1L, owner())).thenReturn(league);
        when(seasonService.currentSeason(league)).thenReturn(season);
        when(matchRepository.findAllBySeasonOrderByRecordedAtDesc(season)).thenReturn(List.of(match));
        when(playerRepository.findAllByLeague(league)).thenReturn(List.of(p1, p2));

        List<MatchResponse> matches = matchService.list(1L, owner());

        assertEquals(1, matches.size());
        assertEquals("P5", matches.get(0).player1Name());
        assertEquals("P6", matches.get(0).player2Name());
    }
}
