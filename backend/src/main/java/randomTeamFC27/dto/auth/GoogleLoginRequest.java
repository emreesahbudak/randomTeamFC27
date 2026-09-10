package randomTeamFC27.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(@NotBlank String idToken, boolean rememberMe) {
}
