package randomTeamFC27.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import randomTeamFC27.dto.auth.AuthResponse;
import randomTeamFC27.dto.auth.GoogleLoginRequest;
import randomTeamFC27.dto.auth.UserSummaryDto;
import randomTeamFC27.entity.OtpChannel;
import randomTeamFC27.entity.User;
import randomTeamFC27.repository.UserRepository;
import randomTeamFC27.security.JwtService;

/**
 * Every method here is transactional as a unit: several touch more than one repository
 * (find-or-create a user, then issue a token), and {@link #refresh} in particular needs the
 * Hibernate session to stay open until {@link JwtService#generateAccessToken} reads the
 * (lazily-loaded) user role off the {@link RefreshTokenService}-supplied proxy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final OtpService otpService;
    private final GoogleAuthService googleAuthService;

    public AuthResponse loginWithGoogle(GoogleLoginRequest request, String deviceInfo) {
        GoogleAuthService.GoogleUserInfo info = googleAuthService.verify(request.idToken());

        User user = userRepository.findByGoogleId(info.googleId())
                .or(() -> userRepository.findByEmail(info.email()))
                .orElseGet(() -> User.builder()
                        .displayName(info.name() != null ? info.name() : info.email())
                        .email(info.email())
                        .build());

        if (user.getGoogleId() == null) {
            user.setGoogleId(info.googleId());
        }
        user = userRepository.save(user);

        return issueTokens(user, request.rememberMe(), deviceInfo);
    }

    public void sendEmailOtp(String email) {
        otpService.sendOtp(email, OtpChannel.EMAIL);
    }

    public AuthResponse verifyEmailOtp(String email, String code, boolean rememberMe, String deviceInfo) {
        otpService.verifyOtp(email, OtpChannel.EMAIL, code);
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(User.builder()
                        .displayName(email)
                        .email(email)
                        .build()));
        return issueTokens(user, rememberMe, deviceInfo);
    }

    public void sendSmsOtp(String phone) {
        otpService.sendOtp(phone, OtpChannel.SMS);
    }

    public AuthResponse verifySmsOtp(String phone, String code, boolean rememberMe, String deviceInfo) {
        otpService.verifyOtp(phone, OtpChannel.SMS, code);
        User user = userRepository.findByPhone(phone)
                .orElseGet(() -> userRepository.save(User.builder()
                        .displayName(phone)
                        .phone(phone)
                        .build()));
        return issueTokens(user, rememberMe, deviceInfo);
    }

    public AuthResponse refresh(String rawRefreshToken, String deviceInfo) {
        RefreshTokenService.RotationResult result = refreshTokenService.rotate(rawRefreshToken, deviceInfo);
        String accessToken = jwtService.generateAccessToken(result.user());
        return new AuthResponse(accessToken, result.rawToken(), UserSummaryDto.from(result.user()));
    }

    public void logout(User currentUser, String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revoke(currentUser, rawRefreshToken);
        }
        log.info("User {} logged out", currentUser.getId());
    }

    private AuthResponse issueTokens(User user, boolean rememberMe, String deviceInfo) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.issue(user, deviceInfo);
        log.info("User {} authenticated (rememberMe={})", user.getId(), rememberMe);
        return new AuthResponse(accessToken, refreshToken, UserSummaryDto.from(user));
    }
}
