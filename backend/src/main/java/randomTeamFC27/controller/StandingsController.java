package randomTeamFC27.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import randomTeamFC27.dto.league.LeagueStatsResponse;
import randomTeamFC27.dto.league.StandingRow;
import randomTeamFC27.security.CurrentUserProvider;
import randomTeamFC27.service.StandingsService;

import java.util.List;

@RestController
@RequestMapping("/api/leagues/{leagueId}")
@RequiredArgsConstructor
@Tag(name = "Standings", description = "League table and stat badges — always computed live from recorded matches, never stored. Owner only.")
public class StandingsController {

    private final StandingsService standingsService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/standings")
    @Operation(operationId = "getStandings", summary = "Full league table (P/W/D/L/GF/GA/GD/Pts per player), sorted by points then goal difference then goals for — owner only")
    public List<StandingRow> standings(@PathVariable Long leagueId) {
        return standingsService.standings(leagueId, currentUserProvider.require());
    }

    @GetMapping("/stats")
    @Operation(operationId = "getLeagueStats", summary = "Golden Boot (most goals), Best Defense (fewest conceded) and head-to-head records between every pair of players who've met — owner only")
    public LeagueStatsResponse stats(@PathVariable Long leagueId) {
        return standingsService.stats(leagueId, currentUserProvider.require());
    }
}
