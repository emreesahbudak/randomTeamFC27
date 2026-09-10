package randomTeamFC27.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import randomTeamFC27.dto.team.LeagueTypeCreateRequest;
import randomTeamFC27.dto.team.LeagueTypeResponse;
import randomTeamFC27.security.CurrentUserProvider;
import randomTeamFC27.service.LeagueTypeService;

import java.util.List;

@RestController
@RequestMapping("/api/league-types")
@RequiredArgsConstructor
@Tag(name = "League Types", description = "Team category lookup (e.g. \"Premier League\") — prevents typo-duplicated categories")
public class LeagueTypeController {

    private final LeagueTypeService leagueTypeService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(operationId = "listLeagueTypes", summary = "List all league types, alphabetically — public, used to populate the team form's dropdown")
    public List<LeagueTypeResponse> list() {
        return leagueTypeService.list().stream().map(LeagueTypeResponse::from).toList();
    }

    @PostMapping
    @Operation(operationId = "createLeagueType", summary = "Create a new league type — ADMIN only. 400 if the name already exists (case-insensitive)")
    public ResponseEntity<LeagueTypeResponse> create(@Valid @RequestBody LeagueTypeCreateRequest request) {
        LeagueTypeResponse response = LeagueTypeResponse.from(
                leagueTypeService.create(request.name(), currentUserProvider.require()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
