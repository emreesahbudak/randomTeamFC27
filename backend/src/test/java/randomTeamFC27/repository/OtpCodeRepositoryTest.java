package randomTeamFC27.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import randomTeamFC27.entity.OtpChannel;
import randomTeamFC27.entity.OtpCode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class OtpCodeRepositoryTest {

    @Autowired
    private OtpCodeRepository otpCodeRepository;

    @Test
    void findsMostRecentUnconsumedCodeForDestination() {
        otpCodeRepository.save(OtpCode.builder()
                .destination("user@example.com").channel(OtpChannel.EMAIL)
                .codeHash("hash-old").expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .consumedAt(Instant.now())
                .build());
        OtpCode latest = otpCodeRepository.save(OtpCode.builder()
                .destination("user@example.com").channel(OtpChannel.EMAIL)
                .codeHash("hash-new").expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .build());

        Optional<OtpCode> found = otpCodeRepository
                .findFirstByDestinationAndChannelAndConsumedAtIsNullOrderByCreatedAtDesc(
                        "user@example.com", OtpChannel.EMAIL);

        assertTrue(found.isPresent());
        assertEquals(latest.getId(), found.get().getId());
    }

    @Test
    void ignoresCodesForDifferentChannel() {
        otpCodeRepository.save(OtpCode.builder()
                .destination("+905551234567").channel(OtpChannel.SMS)
                .codeHash("hash-sms").expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .build());

        Optional<OtpCode> found = otpCodeRepository
                .findFirstByDestinationAndChannelAndConsumedAtIsNullOrderByCreatedAtDesc(
                        "+905551234567", OtpChannel.EMAIL);

        assertTrue(found.isEmpty());
    }
}
