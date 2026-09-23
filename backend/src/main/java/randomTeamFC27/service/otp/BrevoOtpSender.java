package randomTeamFC27.service.otp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import randomTeamFC27.entity.OtpChannel;

import java.util.List;
import java.util.Map;

/**
 * Real email delivery via Brevo's transactional email HTTP API (plain HTTPS, not SMTP) —
 * see {@link OtpSender}'s javadoc for the "swap in a real provider" step this always
 * planned for. Only active under the "prod" profile; {@link LoggingOtpSender} covers
 * everywhere else.
 *
 * Went with an HTTPS API instead of Gmail SMTP because Railway's free/hobby plan blocks
 * all outbound SMTP ports (587/465/25) to prevent spam abuse — a Gmail App Password sender
 * would just hang every request until it timed out. Brevo's free tier (300 emails/day) only
 * needs a single verified sender address (a 6-digit code emailed to it), no domain required.
 *
 * SMS has no real provider wired up yet (still just Twilio-shaped future work, never
 * exposed in any client UI) — that channel just logs a warning here instead of failing.
 */
@Slf4j
@Component
@Profile("prod")
public class BrevoOtpSender implements OtpSender {

    private static final String BREVO_ENDPOINT = "https://api.brevo.com/v3/smtp/email";

    private final RestClient restClient;
    private final String apiKey;
    private final String senderEmail;

    public BrevoOtpSender(@Value("${app.brevo.api-key}") String apiKey,
                           @Value("${app.brevo.sender-email}") String senderEmail) {
        this.restClient = RestClient.create();
        this.apiKey = apiKey;
        this.senderEmail = senderEmail;
    }

    @Override
    public void send(String destination, OtpChannel channel, String code) {
        if (channel != OtpChannel.EMAIL) {
            log.warn("[NO SMS PROVIDER WIRED UP] would send code {} to {}", code, destination);
            return;
        }
        restClient.post()
                .uri(BREVO_ENDPOINT)
                .header("api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "sender", Map.of("email", senderEmail),
                        "to", List.of(Map.of("email", destination)),
                        "subject", "FC27 giriş kodun",
                        "textContent", "Giriş kodun: " + code
                                + "\n\nBu kodu kimseyle paylaşma. 5 dakika içinde geçerliliğini yitirir."))
                .retrieve()
                .toBodilessEntity();
        log.info("Sent EMAIL OTP to {}", destination);
    }
}
