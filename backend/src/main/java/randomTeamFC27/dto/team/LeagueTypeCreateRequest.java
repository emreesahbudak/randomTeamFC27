package randomTeamFC27.dto.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LeagueTypeCreateRequest(@NotBlank @Size(max = 120) String name) {
}
