package randomTeamFC27.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import randomTeamFC27.dto.league.HeadToHeadRow;
import randomTeamFC27.dto.league.LeagueStatsResponse;
import randomTeamFC27.dto.league.StandingRow;
import randomTeamFC27.dto.league.StatLeader;
import randomTeamFC27.entity.League;
import randomTeamFC27.entity.Match;
import randomTeamFC27.entity.Player;
import randomTeamFC27.entity.Season;
import randomTeamFC27.entity.User;
import randomTeamFC27.repository.MatchRepository;
import randomTeamFC27.repository.PlayerRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Standings and stats/badges are always computed live from recorded {@link Match} rows —
 * never stored — so they can never drift out of sync with match history (see the
 * "league standings are never manually editable" rule). Every computation is scoped to one
 * {@link Season} (the league's current open one, or a past closed one under "Geçmiş
 * Ligler") rather than the whole league, so a reset cleanly zeroes only the current view.
 */
@Service
@RequiredArgsConstructor
public class StandingsService {

    private static final int POINTS_FOR_WIN = 3;
    private static final int POINTS_FOR_DRAW = 1;

    private final LeagueService leagueService;
    private final SeasonService seasonService;
    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;

    public List<StandingRow> standings(Long leagueId, User requester) {
        League league = leagueService.getOwned(leagueId, requester);
        return standingsForSeason(league, seasonService.currentSeason(league));
    }

    public List<StandingRow> standingsForPastSeason(Long leagueId, Long seasonId, User requester) {
        League league = leagueService.getOwned(leagueId, requester);
        return standingsForSeason(league, seasonService.getPastSeason(league, seasonId));
    }

    public LeagueStatsResponse stats(Long leagueId, User requester) {
        League league = leagueService.getOwned(leagueId, requester);
        return statsForSeason(league, seasonService.currentSeason(league));
    }

    public LeagueStatsResponse statsForPastSeason(Long leagueId, Long seasonId, User requester) {
        League league = leagueService.getOwned(leagueId, requester);
        return statsForSeason(league, seasonService.getPastSeason(league, seasonId));
    }

    private List<StandingRow> standingsForSeason(League league, Season season) {
        List<Match> matches = matchRepository.findAllBySeasonOrderByRecordedAtDesc(season);
        Map<Long, Accumulator> byPlayer = initAccumulators(league, matches);

        for (Match match : matches) {
            Accumulator a1 = byPlayer.get(match.getPlayer1().getId());
            Accumulator a2 = byPlayer.get(match.getPlayer2().getId());
            if (a1 == null || a2 == null) continue; // defensive — should never happen
            a1.apply(match.getScore1(), match.getScore2());
            a2.apply(match.getScore2(), match.getScore1());
        }

        return byPlayer.values().stream()
                .map(Accumulator::toRow)
                .sorted(Comparator.comparingInt(StandingRow::points).reversed()
                        .thenComparing(Comparator.comparingInt(StandingRow::goalDifference).reversed())
                        .thenComparing(Comparator.comparingInt(StandingRow::goalsFor).reversed())
                        .thenComparing(StandingRow::displayName))
                .toList();
    }

    private LeagueStatsResponse statsForSeason(League league, Season season) {
        List<Match> matches = matchRepository.findAllBySeasonOrderByRecordedAtDesc(season);
        Map<Long, Accumulator> byPlayer = initAccumulators(league, matches);

        for (Match match : matches) {
            Accumulator a1 = byPlayer.get(match.getPlayer1().getId());
            Accumulator a2 = byPlayer.get(match.getPlayer2().getId());
            if (a1 == null || a2 == null) continue;
            a1.apply(match.getScore1(), match.getScore2());
            a2.apply(match.getScore2(), match.getScore1());
        }

        StatLeader topScorer = byPlayer.values().stream()
                .filter(a -> a.played > 0)
                .max(Comparator.comparingInt(a -> a.goalsFor))
                .map(a -> new StatLeader(a.playerId, a.displayName, a.goalsFor))
                .orElse(null);
        StatLeader bestDefense = byPlayer.values().stream()
                .filter(a -> a.played > 0)
                .min(Comparator.comparingInt(a -> a.goalsAgainst))
                .map(a -> new StatLeader(a.playerId, a.displayName, a.goalsAgainst))
                .orElse(null);

        return new LeagueStatsResponse(topScorer, bestDefense, headToHead(matches, byPlayer));
    }

    /**
     * A player appears in a season's table if they're still active (current roster) OR they
     * played at least one match in that specific season — so removing a player drops them
     * out of the *live* standings going forward, but a past/closed season still shows
     * exactly who played back then, and a player removed mid-season doesn't lose the
     * matches they already contributed to the still-open season.
     */
    private Map<Long, Accumulator> initAccumulators(League league, List<Match> seasonMatches) {
        Set<Long> playedInSeason = new HashSet<>();
        for (Match match : seasonMatches) {
            playedInSeason.add(match.getPlayer1().getId());
            playedInSeason.add(match.getPlayer2().getId());
        }
        Map<Long, Accumulator> byPlayer = new LinkedHashMap<>();
        for (Player player : playerRepository.findAllByLeague(league)) {
            if (player.isActive() || playedInSeason.contains(player.getId())) {
                byPlayer.put(player.getId(), new Accumulator(player.getId(), player.getDisplayName()));
            }
        }
        return byPlayer;
    }

    /**
     * One row per unordered pair that has played at least once, keyed so the same pair
     * always aggregates together regardless of which player was "player1" in a given match.
     * Names are resolved via {@code byPlayer} (built from the non-proxy, fully-loaded
     * {@link Player} list) rather than {@code match.getPlayer1()/getPlayer2()} directly —
     * those relations are LAZY, and by the time this response is serialized the session
     * that loaded the matches has already closed (see MatchService's identical note).
     */
    private List<HeadToHeadRow> headToHead(List<Match> matches, Map<Long, Accumulator> byPlayer) {
        Map<String, H2HAccumulator> byPair = new LinkedHashMap<>();
        for (Match match : matches) {
            Long p1Id = match.getPlayer1().getId(); // safe: ID-only access never hits the DB
            Long p2Id = match.getPlayer2().getId();
            boolean p1First = p1Id < p2Id;
            Long firstId = p1First ? p1Id : p2Id;
            Long secondId = p1First ? p2Id : p1Id;
            int firstScore = p1First ? match.getScore1() : match.getScore2();
            int secondScore = p1First ? match.getScore2() : match.getScore1();

            String key = firstId + ":" + secondId;
            H2HAccumulator acc = byPair.computeIfAbsent(key, k -> new H2HAccumulator(firstId, secondId));
            if (firstScore > secondScore) acc.firstWins++;
            else if (firstScore < secondScore) acc.secondWins++;
            else acc.draws++;
        }
        List<HeadToHeadRow> rows = new ArrayList<>();
        for (H2HAccumulator acc : byPair.values()) {
            rows.add(new HeadToHeadRow(
                    acc.firstId, byPlayer.get(acc.firstId).displayName,
                    acc.secondId, byPlayer.get(acc.secondId).displayName,
                    acc.firstWins, acc.secondWins, acc.draws));
        }
        return rows;
    }

    private static class Accumulator {
        final Long playerId;
        final String displayName;
        int played, won, drawn, lost, goalsFor, goalsAgainst;

        Accumulator(Long playerId, String displayName) {
            this.playerId = playerId;
            this.displayName = displayName;
        }

        void apply(int scored, int conceded) {
            played++;
            goalsFor += scored;
            goalsAgainst += conceded;
            if (scored > conceded) won++;
            else if (scored < conceded) lost++;
            else drawn++;
        }

        StandingRow toRow() {
            int points = won * POINTS_FOR_WIN + drawn * POINTS_FOR_DRAW;
            return new StandingRow(playerId, displayName, played, won, drawn, lost, goalsFor, goalsAgainst, goalsFor - goalsAgainst, points);
        }
    }

    private static class H2HAccumulator {
        final Long firstId;
        final Long secondId;
        int firstWins, secondWins, draws;

        H2HAccumulator(Long firstId, Long secondId) {
            this.firstId = firstId;
            this.secondId = secondId;
        }
    }
}
