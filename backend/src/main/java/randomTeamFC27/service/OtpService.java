package randomTeamFC27.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import randomTeamFC27.entity.OtpChannel;
import randomTeamFC27.entity.OtpCode;
import randomTeamFC27.exception.ApiException;
import randomTeamFC27.repository.OtpCodeRepository;
import randomTeamFC27.security.HashUtil;
import randomTeamFC27.service.otp.OtpSender;
import randomTeamFC27.service.ratelimit.RateLimiter;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final int MAX_VERIFY_ATTEMPTS = 5;

    private final OtpCodeRepository otpCodeRepository;
    private final OtpSender otpSender;
    private final RateLimiter rateLimiter;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.otp.code-ttl-minutes}")
    private long codeTtlMinutes;

    @Value("${app.otp.rate-limit.max-requests}")
    private int rateLimitMaxRequests;

    @Value("${app.otp.rate-limit.window-minutes}")
    private long rateLimitWindowMinutes;

    public void sendOtp(String destination, OtpChannel channel) {
        String rateLimitKey = channel + ":" + destination;
        if (!rateLimiter.tryConsume(rateLimitKey, rateLimitMaxRequests, Duration.ofMinutes(rateLimitWindowMinutes).getSeconds())) {
            log.warn("OTP rate limit exceeded for {}", rateLimitKey);
            throw ApiException.tooManyRequests("Çok fazla kod istendi — birazdan tekrar dene");
        }

        String code = generateCode();
        OtpCode otp = OtpCode.builder()
                .destination(destination)
                .channel(channel)
                .codeHash(HashUtil.sha256Hex(code))
                .expiresAt(Instant.now().plus(Duration.ofMinutes(codeTtlMinutes)))
                .build();
        otpCodeRepository.save(otp);

        otpSender.send(destination, channel, code);
        log.info("Sent {} OTP to {}", channel, destination);
    }

    /**
     * Marks the most recent unconsumed code for {@code destination}/{@code channel} as
     * consumed if it matches. Throws {@link ApiException} on any failure — expired,
     * wrong code, too many attempts, or nothing pending.
     */
    public void verifyOtp(String destination, OtpChannel channel, String code) {
        OtpCode otp = otpCodeRepository
                .findFirstByDestinationAndChannelAndConsumedAtIsNullOrderByCreatedAtDesc(destination, channel)
                .orElseThrow(() -> ApiException.badRequest("Bu adrese/numaraya kod gönderilmemiş"));

        if (otp.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("Kodun süresi doldu — yeni bir kod iste");
        }
        if (otp.getAttempts() >= MAX_VERIFY_ATTEMPTS) {
            throw ApiException.badRequest("Çok fazla yanlış deneme — yeni bir kod iste");
        }

        if (!otp.getCodeHash().equals(HashUtil.sha256Hex(code))) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpCodeRepository.save(otp);
            log.warn("Incorrect OTP attempt for {} ({} of {})", destination, otp.getAttempts(), MAX_VERIFY_ATTEMPTS);
            throw ApiException.badRequest("Kod yanlış");
        }

        otp.setConsumedAt(Instant.now());
        otpCodeRepository.save(otp);
        log.info("Verified {} OTP for {}", channel, destination);
    }

    private String generateCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }
}
