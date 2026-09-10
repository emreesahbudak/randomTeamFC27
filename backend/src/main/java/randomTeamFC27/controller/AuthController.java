package randomTeamFC27.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import randomTeamFC27.dto.auth.AuthResponse;
import randomTeamFC27.dto.auth.EmailOtpSendRequest;
import randomTeamFC27.dto.auth.EmailOtpVerifyRequest;
import randomTeamFC27.dto.auth.GoogleLoginRequest;
import randomTeamFC27.dto.auth.RefreshRequest;
import randomTeamFC27.dto.auth.SmsOtpSendRequest;
import randomTeamFC27.dto.auth.SmsOtpVerifyRequest;
import randomTeamFC27.entity.User;
import randomTeamFC27.exception.ApiException;
import randomTeamFC27.security.CurrentUserProvider;
import randomTeamFC27.service.AuthService;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Google / Email OTP / SMS OTP login, refresh-token rotation, logout")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";

    private final AuthService authService;
    private final CurrentUserProvider currentUserProvider;

    @Value("${app.jwt.refresh-token-ttl-days}")
    private long refreshTokenTtlDays;

    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    @PostMapping("/google")
    @Operation(summary = "Sign in (or register) with a Google ID token — public")
    public ResponseEntity<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request,
                                                          HttpServletRequest servletRequest) {
        AuthResponse response = authService.loginWithGoogle(request, deviceInfo(servletRequest));
        return withRefreshCookie(response, request.rememberMe());
    }

    @PostMapping("/otp/email/send")
    @Operation(summary = "Send a 6-digit email login code — public, rate-limited")
    public ResponseEntity<Void> sendEmailOtp(@Valid @RequestBody EmailOtpSendRequest request) {
        authService.sendEmailOtp(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/otp/email/verify")
    @Operation(summary = "Verify the emailed code and sign in (or register) — public")
    public ResponseEntity<AuthResponse> verifyEmailOtp(@Valid @RequestBody EmailOtpVerifyRequest request,
                                                         HttpServletRequest servletRequest) {
        AuthResponse response = authService.verifyEmailOtp(
                request.email(), request.code(), request.rememberMe(), deviceInfo(servletRequest));
        return withRefreshCookie(response, request.rememberMe());
    }

    @PostMapping("/otp/sms/send")
    @Operation(summary = "Send a 6-digit SMS login code — public, rate-limited")
    public ResponseEntity<Void> sendSmsOtp(@Valid @RequestBody SmsOtpSendRequest request) {
        authService.sendSmsOtp(request.phone());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/otp/sms/verify")
    @Operation(summary = "Verify the texted code and sign in (or register) — public")
    public ResponseEntity<AuthResponse> verifySmsOtp(@Valid @RequestBody SmsOtpVerifyRequest request,
                                                       HttpServletRequest servletRequest) {
        AuthResponse response = authService.verifySmsOtp(
                request.phone(), request.code(), request.rememberMe(), deviceInfo(servletRequest));
        return withRefreshCookie(response, request.rememberMe());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token (body or cookie) for a new access+refresh pair — public")
    public ResponseEntity<AuthResponse> refresh(@RequestBody(required = false) RefreshRequest request,
                                                  @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String cookieToken,
                                                  HttpServletRequest servletRequest) {
        String rawToken = resolveRefreshToken(request, cookieToken);
        AuthResponse response = authService.refresh(rawToken, deviceInfo(servletRequest));
        // A cookie was already present, or the caller wouldn't have a token to refresh —
        // keep behaving the same way (rotate the cookie) so long-lived web sessions persist.
        return withRefreshCookie(response, cookieToken != null);
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the caller's refresh token and clear the cookie — requires a valid access token")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest request,
                                        @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String cookieToken) {
        User currentUser = currentUserProvider.require();
        String rawToken = request != null && request.refreshToken() != null ? request.refreshToken() : cookieToken;
        authService.logout(currentUser, rawToken);

        ResponseCookie clearCookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(0)
                .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
    }

    private String resolveRefreshToken(RefreshRequest request, String cookieToken) {
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            return request.refreshToken();
        }
        if (cookieToken != null && !cookieToken.isBlank()) {
            return cookieToken;
        }
        throw ApiException.unauthorized("Refresh token bulunamadı");
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(AuthResponse response, boolean rememberMe) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (rememberMe) {
            ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, response.refreshToken())
                    .httpOnly(true)
                    .secure(cookieSecure)
                    .sameSite("Lax")
                    .path("/api/auth")
                    .maxAge(Duration.ofDays(refreshTokenTtlDays))
                    .build();
            builder.header(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        return builder.body(response);
    }

    private String deviceInfo(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null ? userAgent : "unknown";
    }
}
