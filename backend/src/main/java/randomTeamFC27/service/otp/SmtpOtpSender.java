package randomTeamFC27.service.otp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import randomTeamFC27.entity.OtpChannel;

/**
 * Real email delivery via Gmail SMTP (an account App Password, configured through
 * spring.mail.username/password — see application.yaml) — the "swap in a real provider"
 * step {@link OtpSender}'s javadoc always planned for. Only active under the "prod" profile;
 * {@link LoggingOtpSender} covers everywhere else.
 *
 * SMS has no real provider wired up yet (still just Twilio-shaped future work, never
 * exposed in any client UI) — that channel just logs a warning here instead of failing.
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class SmtpOtpSender implements OtpSender {

    private final JavaMailSender mailSender;

    @Override
    public void send(String destination, OtpChannel channel, String code) {
        if (channel != OtpChannel.EMAIL) {
            log.warn("[NO SMS PROVIDER WIRED UP] would send code {} to {}", code, destination);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(destination);
        message.setSubject("FC27 giriş kodun");
        message.setText("Giriş kodun: " + code + "\n\nBu kodu kimseyle paylaşma. 5 dakika içinde geçerliliğini yitirir.");
        mailSender.send(message);
        log.info("Sent EMAIL OTP to {}", destination);
    }
}
