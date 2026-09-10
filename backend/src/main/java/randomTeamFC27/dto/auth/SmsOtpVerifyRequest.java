package randomTeamFC27.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SmsOtpVerifyRequest(
        @NotBlank @Pattern(regexp = "\\+?[0-9]{7,15}") String phone,
        @NotBlank @Pattern(regexp = "\\d{6}") String code,
        boolean rememberMe) {
}
