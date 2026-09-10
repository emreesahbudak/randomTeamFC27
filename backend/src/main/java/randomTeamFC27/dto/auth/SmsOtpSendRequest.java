package randomTeamFC27.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SmsOtpSendRequest(@NotBlank @Pattern(regexp = "\\+?[0-9]{7,15}") String phone) {
}
