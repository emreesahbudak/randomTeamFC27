package randomTeamFC27.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import randomTeamFC27.dto.league.LeagueCreateRequest;
import randomTeamFC27.dto.league.LeagueResponse;
import randomTeamFC27.security.CurrentUserProvider;
import randomTeamFC27.service.LeagueService;

import java.util.List;

@RestController
@RequestMapping("/api/leagues")
@RequiredArgsConstructor
@Tag(name = "Leagues", description = "A friend group's league — container for players and recorded matches. Requires authentication (no guest access); every operation is scoped to leagues the caller owns.")
public class LeagueController {

    private final LeagueService leagueService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    @Operation(operationId = "createLeague", summary = "Create a league owned by the current user — requires auth")
    public ResponseEntity<LeagueResponse> create(@Valid @RequestBody LeagueCreateRequest request) {
        LeagueResponse response = LeagueResponse.from(leagueService.create(request.name(), currentUserProvider.require()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(operationId = "listMyLeagues", summary = "List leagues owned by the current user — requires auth")
    public List<LeagueResponse> listMine() {
        return leagueService.listMine(currentUserProvider.require()).stream().map(LeagueResponse::from).toList();
    }

    @PostMapping("/{id}/reset")
    @Operation(operationId = "resetLeague", summary = "Close the league's current season and open a new one — current standings/matches go back to zero, but the closed season's scores/standings stay browsable under GET /api/leagues/{id}/seasons (\"Geçmiş Ligler\"); the league and its player roster are kept — owner only")
    public ResponseEntity<Void> reset(@PathVariable Long id) {
        leagueService.resetMatches(id, currentUserProvider.require());
        return ResponseEntity.noContent().build();
    }
}
