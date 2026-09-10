package randomTeamFC27.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import randomTeamFC27.dto.league.LeagueStatsResponse;
import randomTeamFC27.dto.league.MatchResponse;
import randomTeamFC27.dto.league.SeasonResponse;
import randomTeamFC27.dto.league.StandingRow;
import randomTeamFC27.entity.League;
import randomTeamFC27.security.CurrentUserProvider;
import randomTeamFC27.service.LeagueService;
import randomTeamFC27.service.MatchService;
import randomTeamFC27.service.SeasonService;
import randomTeamFC27.service.StandingsService;

import java.util.List;

/**
 * "Geçmiş Ligler" (past leagues) — every season a league had before its most recent
 * "reset league". The current season's standings/matches/stats stay on the un-prefixed
 * {@code /api/leagues/{leagueId}/standings|matches|stats} endpoints; this controller is
 * read-only history for everything closed out by a reset.
 */
@RestController
@RequestMapping("/api/leagues/{leagueId}/seasons")
@RequiredArgsConstructor
@Tag(name = "Seasons", description = "Past (closed) seasons of a league — frozen standings/matches from before each \"reset league\". Owner only.")
public class SeasonController {

    private final LeagueService leagueService;
    private final SeasonService seasonService;
    private final StandingsService standingsService;
    private final MatchService matchService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(operationId = "listPastSeasons", summary = "List the league's past (closed) seasons, most recently closed first — owner only")
    public List<SeasonResponse> listPast(@PathVariable Long leagueId) {
        League league = leagueService.getOwned(leagueId, currentUserProvider.require());
        return seasonService.pastSeasons(league).stream().map(SeasonResponse::from).toList();
    }

    @GetMapping("/{seasonId}/standings")
    @Operation(operationId = "getPastSeasonStandings", summary = "Standings for one past season, frozen at the point it was closed — owner only")
    public List<StandingRow> pastStandings(@PathVariable Long leagueId, @PathVariable Long seasonId) {
        return standingsService.standingsForPastSeason(leagueId, seasonId, currentUserProvider.require());
    }

    @GetMapping("/{seasonId}/stats")
    @Operation(operationId = "getPastSeasonStats", summary = "Golden Boot / Best Defense / head-to-head for one past season — owner only")
    public LeagueStatsResponse pastStats(@PathVariable Long leagueId, @PathVariable Long seasonId) {
        return standingsService.statsForPastSeason(leagueId, seasonId, currentUserProvider.require());
    }

    @GetMapping("/{seasonId}/matches")
    @Operation(operationId = "getPastSeasonMatches", summary = "Match history for one past season — owner only")
    public List<MatchResponse> pastMatches(@PathVariable Long leagueId, @PathVariable Long seasonId) {
        return matchService.listForPastSeason(leagueId, seasonId, currentUserProvider.require());
    }
}
