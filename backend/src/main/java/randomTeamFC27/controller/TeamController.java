package randomTeamFC27.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import randomTeamFC27.dto.team.TeamCreateRequest;
import randomTeamFC27.dto.team.TeamPatchRequest;
import randomTeamFC27.dto.team.TeamResponse;
import randomTeamFC27.security.CurrentUserProvider;
import randomTeamFC27.service.TeamService;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@Tag(name = "Teams", description = "Team pool used by the wheel, managed via full CRUD by admins")
public class TeamController {

    private final TeamService teamService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(operationId = "listTeams", summary = "List teams, optionally filtered by star level(s) and/or league type — public, used by the wheel including for guests. Only active teams unless includeInactive=true (admin management view).")
    public List<TeamResponse> list(
            @Parameter(description = "Comma-separated star levels, e.g. 2,3,4") @RequestParam(required = false) List<Integer> stars,
            @Parameter(description = "Id from GET /api/league-types") @RequestParam(required = false) Long leagueTypeId,
            @Parameter(description = "Include deactivated teams too — for the admin management view") @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        return teamService.list(stars, leagueTypeId, includeInactive).stream().map(TeamResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getTeam", summary = "Get a single team by id — public")
    public TeamResponse get(@PathVariable Long id) {
        return TeamResponse.from(teamService.get(id));
    }

    @PostMapping
    @Operation(operationId = "createTeam", summary = "Create a team — ADMIN only")
    public ResponseEntity<TeamResponse> create(@Valid @RequestBody TeamCreateRequest request) {
        TeamResponse response = TeamResponse.from(teamService.create(request, currentUserProvider.require()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(operationId = "replaceTeam", summary = "Replace every field of a team — ADMIN only")
    public TeamResponse replace(@PathVariable Long id, @Valid @RequestBody TeamCreateRequest request) {
        return TeamResponse.from(teamService.replace(id, request, currentUserProvider.require()));
    }

    @PatchMapping("/{id}")
    @Operation(operationId = "patchTeam", summary = "Update only the given fields of a team (null = unchanged) — ADMIN only")
    public TeamResponse patch(@PathVariable Long id, @Valid @RequestBody TeamPatchRequest request) {
        return TeamResponse.from(teamService.patch(id, request, currentUserProvider.require()));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deactivateTeam", summary = "Deactivate a team (soft delete — preserves match history) — ADMIN only")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        teamService.deactivate(id, currentUserProvider.require());
        return ResponseEntity.noContent().build();
    }
}
