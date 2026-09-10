package randomTeamFC27.dto.auth;

/**
 * {@code refreshToken} is always populated (mobile clients read it directly into
 * SecureStore); web clients that requested "remember me" additionally receive it as an
 * HTTP-only cookie and are expected to ignore this field.
 */
public record AuthResponse(String accessToken, String refreshToken, UserSummaryDto user) {
}
