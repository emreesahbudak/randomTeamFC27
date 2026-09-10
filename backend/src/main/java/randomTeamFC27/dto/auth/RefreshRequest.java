package randomTeamFC27.dto.auth;

/**
 * Mobile clients send the refresh token in the body (from SecureStore). Web clients may
 * omit this entirely and rely on the HTTP-only {@code refresh_token} cookie instead.
 */
public record RefreshRequest(String refreshToken) {
}
