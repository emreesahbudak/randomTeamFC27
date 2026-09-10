package randomTeamFC27.service.otp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import randomTeamFC27.entity.OtpChannel;

/**
 * Mock OTP delivery — logs the code instead of sending a real email/SMS. Active everywhere
 * except the "prod" profile (see {@link SmtpOtpSender}), which keeps local dev and tests
 * fully OTP-testable without any real mail account configured.
 */
@Slf4j
@Component
@Profile("!prod")
public class LoggingOtpSender implements OtpSender {

    @Override
    public void send(String destination, OtpChannel channel, String code) {
        log.info("[MOCK {} OTP] would send code {} to {}", channel, code, destination);
    }
}
