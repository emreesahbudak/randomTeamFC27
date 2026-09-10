package randomTeamFC27.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import randomTeamFC27.dto.league.PlayerCreateRequest;
import randomTeamFC27.dto.league.PlayerResponse;
import randomTeamFC27.security.CurrentUserProvider;
import randomTeamFC27.service.PlayerService;

import java.util.List;

@RestController
@RequestMapping("/api/leagues/{leagueId}/players")
@RequiredArgsConstructor
@Tag(name = "Players", description = "A league's roster — a display name only, optionally linked to a registered user. Owner only.")
public class PlayerController {

    private final PlayerService playerService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    @Operation(operationId = "addPlayer", summary = "Add a player to the league's roster — owner only. 400 if the display name is already used in this league")
    public ResponseEntity<PlayerResponse> add(@PathVariable Long leagueId, @Valid @RequestBody PlayerCreateRequest request) {
        PlayerResponse response = PlayerResponse.from(
                playerService.add(leagueId, request.displayName(), request.userId(), currentUserProvider.require()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(operationId = "listPlayers", summary = "List the league's roster — owner only. Only active players unless includeInactive=true")
    public List<PlayerResponse> list(
            @PathVariable Long leagueId,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        return playerService.list(leagueId, currentUserProvider.require(), includeInactive).stream().map(PlayerResponse::from).toList();
    }

    @DeleteMapping("/{playerId}")
    @Operation(operationId = "removePlayer", summary = "Remove a player from the roster (soft — past matches keep their record) — owner only")
    public ResponseEntity<Void> remove(@PathVariable Long leagueId, @PathVariable Long playerId) {
        playerService.remove(leagueId, playerId, currentUserProvider.require());
        return ResponseEntity.noContent().build();
    }
}
