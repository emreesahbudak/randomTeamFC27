package randomTeamFC27.dto.league;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LeagueCreateRequest(@NotBlank @Size(max = 120) String name) {
}
