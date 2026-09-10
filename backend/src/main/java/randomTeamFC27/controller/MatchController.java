package randomTeamFC27.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import randomTeamFC27.dto.league.MatchCreateRequest;
import randomTeamFC27.dto.league.MatchResponse;
import randomTeamFC27.security.CurrentUserProvider;
import randomTeamFC27.service.MatchService;

import java.util.List;

@RestController
@RequestMapping("/api/leagues/{leagueId}/matches")
@RequiredArgsConstructor
@Tag(name = "Matches", description = "Recorded match results within a league — a row only ever exists once a final score is registered. Owner only.")
public class MatchController {

    private final MatchService matchService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    @Operation(operationId = "recordMatch", summary = "Register a final score between two of the league's players (the wheel's draw) — owner only")
    public ResponseEntity<MatchResponse> record(@PathVariable Long leagueId, @Valid @RequestBody MatchCreateRequest request) {
        MatchResponse response = matchService.record(leagueId, request, currentUserProvider.require());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(operationId = "listMatches", summary = "List the league's recorded matches, most recent first — owner only")
    public List<MatchResponse> list(@PathVariable Long leagueId) {
        return matchService.list(leagueId, currentUserProvider.require());
    }
}
