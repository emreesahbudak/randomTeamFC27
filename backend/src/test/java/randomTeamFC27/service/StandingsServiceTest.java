package randomTeamFC27.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import randomTeamFC27.dto.league.LeagueStatsResponse;
import randomTeamFC27.dto.league.StandingRow;
import randomTeamFC27.entity.League;
import randomTeamFC27.entity.Match;
import randomTeamFC27.entity.Player;
import randomTeamFC27.entity.Role;
import randomTeamFC27.entity.Season;
import randomTeamFC27.entity.User;
import randomTeamFC27.repository.MatchRepository;
import randomTeamFC27.repository.PlayerRepository;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StandingsServiceTest {

    @Mock
    private LeagueService leagueService;
    @Mock
    private SeasonService seasonService;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private MatchRepository matchRepository;

    private StandingsService standingsService;

    @BeforeEach
    void setUp() {
        standingsService = new StandingsService(leagueService, seasonService, playerRepository, matchRepository);
    }

    private User owner() {
        return User.builder().id(1L).displayName("Owner").role(Role.USER).build();
    }

    private League league() {
        return League.builder().id(1L).name("Friday Nights").owner(owner()).build();
    }

    private Player player(long id, String name) {
        return Player.builder().id(id).league(league()).displayName(name).active(true).build();
    }

    private Player removedPlayer(long id, String name) {
        return Player.builder().id(id).league(league()).displayName(name).active(false).build();
    }

    private Match match(Player p1, Player p2, int s1, int s2) {
        return Match.builder().id((long) (Math.random() * 1_000_000)).league(league())
                .player1(p1).player2(p2).score1(s1).score2(s2).recordedAt(Instant.now()).build();
    }

    @Test
    void standingsComputesPointsWinsDrawsLossesAndGoalDifference() {
        League league = league();
        Season season = Season.builder().id(1L).league(league).name("Sezon 1").startedAt(Instant.now()).build();
        Player ahmet = player(1L, "Ahmet");
        Player mehmet = player(2L, "Mehmet");
        when(leagueService.getOwned(1L, owner())).thenReturn(league);
        when(seasonService.currentSeason(league)).thenReturn(season);
        when(playerRepository.findAllByLeague(league)).thenReturn(List.of(ahmet, mehmet));
        when(matchRepository.findAllBySeasonOrderByRecordedAtDesc(season)).thenReturn(List.of(
                match(ahmet, mehmet, 3, 1), // Ahmet win
                match(ahmet, mehmet, 0, 0), // draw
                match(mehmet, ahmet, 2, 0)  // Mehmet win (score1=2 for mehmet, score2=0 for ahmet)
        ));

        List<StandingRow> rows = standingsService.standings(1L, owner());

        StandingRow ahmetRow = rows.stream().filter(r -> r.playerId() == 1L).findFirst().orElseThrow();
        StandingRow mehmetRow = rows.stream().filter(r -> r.playerId() == 2L).findFirst().orElseThrow();

        assertEquals(3, ahmetRow.played());
        assertEquals(1, ahmetRow.won());
        assertEquals(1, ahmetRow.drawn());
        assertEquals(1, ahmetRow.lost());
        assertEquals(3, ahmetRow.goalsFor());
        assertEquals(3, ahmetRow.goalsAgainst());
        assertEquals(0, ahmetRow.goalDifference());
        assertEquals(4, ahmetRow.points()); // 1 win (3) + 1 draw (1)

        assertEquals(3, mehmetRow.played());
        assertEquals(1, mehmetRow.won());
        assertEquals(1, mehmetRow.drawn());
        assertEquals(1, mehmetRow.lost());
        assertEquals(4, mehmetRow.points());
    }

    @Test
    void standingsSortsByPointsThenGoalDifference() {
        League league = league();
        Season season = Season.builder().id(1L).league(league).name("Sezon 1").startedAt(Instant.now()).build();
        Player a = player(1L, "A");
        Player b = player(2L, "B");
        when(leagueService.getOwned(1L, owner())).thenReturn(league);
        when(seasonService.currentSeason(league)).thenReturn(season);
        when(playerRepository.findAllByLeague(league)).thenReturn(List.of(a, b));
        // A wins big (more points AND better GD) — must sort first
        when(matchRepository.findAllBySeasonOrderByRecordedAtDesc(season)).thenReturn(List.of(
                match(a, b, 5, 0)
        ));

        List<StandingRow> rows = standingsService.standings(1L, owner());

        assertEquals("A", rows.get(0).displayName());
        assertEquals("B", rows.get(1).displayName());
    }

    @Test
    void playerWithNoMatchesHasZeroedRow() {
        League league = league();
        Season season = Season.builder().id(1L).league(league).name("Sezon 1").startedAt(Instant.now()).build();
        Player lonely = player(1L, "Lonely");
        when(leagueService.getOwned(1L, owner())).thenReturn(league);
        when(seasonService.currentSeason(league)).thenReturn(season);
        when(playerRepository.findAllByLeague(league)).thenReturn(List.of(lonely));
        when(matchRepository.findAllBySeasonOrderByRecordedAtDesc(season)).thenReturn(List.of());

        List<StandingRow> rows = standingsService.standings(1L, owner());

        assertEquals(1, rows.size());
        assertEquals(0, rows.get(0).played());
        assertEquals(0, rows.get(0).points());
    }

    @Test
    void removedPlayerWithNoMatchesThisSeasonDropsOutOfStandings() {
        League league = league();
        Season season = Season.builder().id(1L).league(league).name("Sezon 1").startedAt(Instant.now()).build();
        Player active = player(1L, "Ahmet");
        Player removedNoMatches = removedPlayer(2L, "Eski Oyuncu"); // played in a now-closed season only
        when(leagueService.getOwned(1L, owner())).thenReturn(league);
        when(seasonService.currentSeason(league)).thenReturn(season);
        when(playerRepository.findAllByLeague(league)).thenReturn(List.of(active, removedNoMatches));
        when(matchRepository.findAllBySeasonOrderByRecordedAtDesc(season)).thenReturn(List.of());

        List<StandingRow> rows = standingsService.standings(1L, owner());

        assertEquals(1, rows.size());
        assertEquals("Ahmet", rows.get(0).displayName());
    }

    @Test
    void removedPlayerWhoPlayedThisSeasonBeforeBeingRemovedStaysInStandings() {
        League league = league();
        Season season = Season.builder().id(1L).league(league).name("Sezon 1").startedAt(Instant.now()).build();
        Player active = player(1L, "Ahmet");
        Player removedButPlayed = removedPlayer(2L, "Ayrildi"); // removed after playing this season
        when(leagueService.getOwned(1L, owner())).thenReturn(league);
        when(seasonService.currentSeason(league)).thenReturn(season);
        when(playerRepository.findAllByLeague(league)).thenReturn(List.of(active, removedButPlayed));
        when(matchRepository.findAllBySeasonOrderByRecordedAtDesc(season)).thenReturn(List.of(
                match(active, removedButPlayed, 2, 1)
        ));

        List<StandingRow> rows = standingsService.standings(1L, owner());

        assertEquals(2, rows.size());
        assertEquals(1, rows.stream().filter(r -> r.displayName().equals("Ayrildi")).findFirst().orElseThrow().played());
    }

    @Test
    void statsReturnsNullLeadersWhenNoMatchesPlayed() {
        League league = league();
        Season season = Season.builder().id(1L).league(league).name("Sezon 1").startedAt(Instant.now()).build();
        when(leagueService.getOwned(1L, owner())).thenReturn(league);
        when(seasonService.currentSeason(league)).thenReturn(season);
        when(playerRepository.findAllByLeague(league)).thenReturn(List.of(player(1L, "Solo")));
        when(matchRepository.findAllBySeasonOrderByRecordedAtDesc(season)).thenReturn(List.of());

        LeagueStatsResponse stats = standingsService.stats(1L, owner());

        assertNull(stats.topScorer());
        assertNull(stats.bestDefense());
        assertEquals(0, stats.headToHead().size());
    }

    @Test
    void statsPicksTopScorerAndBestDefenseAndBuildsHeadToHead() {
        League league = league();
        Season season = Season.builder().id(1L).league(league).name("Sezon 1").startedAt(Instant.now()).build();
        Player ahmet = player(1L, "Ahmet");   // attacker: scores a lot, leaks goals too
        Player mehmet = player(2L, "Mehmet"); // defensive: scores little, concedes almost nothing
        Player zeynep = player(3L, "Zeynep"); // punching bag for both
        when(leagueService.getOwned(1L, owner())).thenReturn(league);
        when(seasonService.currentSeason(league)).thenReturn(season);
        when(playerRepository.findAllByLeague(league)).thenReturn(List.of(ahmet, mehmet, zeynep));
        when(matchRepository.findAllBySeasonOrderByRecordedAtDesc(season)).thenReturn(List.of(
                match(ahmet, zeynep, 4, 3),  // Ahmet: GF 4, GA 3
                match(mehmet, zeynep, 1, 0)  // Mehmet: GF 1, GA 0
        ));

        LeagueStatsResponse stats = standingsService.stats(1L, owner());

        assertEquals(1L, stats.topScorer().playerId());   // Ahmet: 4 goals, highest
        assertEquals(4, stats.topScorer().value());
        assertEquals(2L, stats.bestDefense().playerId());  // Mehmet: 0 conceded, lowest
        assertEquals(0, stats.bestDefense().value());
        assertEquals(2, stats.headToHead().size());        // Ahmet-Zeynep, Mehmet-Zeynep
    }
}
