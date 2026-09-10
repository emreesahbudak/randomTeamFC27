package randomTeamFC27.dto.league;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code userId} is optional — a player can be added by display name only, without linking
 *  a registered account (see {@link randomTeamFC27.entity.Player}). */
public record PlayerCreateRequest(@NotBlank @Size(max = 120) String displayName, Long userId) {
}
