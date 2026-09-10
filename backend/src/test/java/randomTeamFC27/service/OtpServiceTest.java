package randomTeamFC27.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import randomTeamFC27.entity.OtpChannel;
import randomTeamFC27.entity.OtpCode;
import randomTeamFC27.exception.ApiException;
import randomTeamFC27.repository.OtpCodeRepository;
import randomTeamFC27.security.HashUtil;
import randomTeamFC27.service.otp.OtpSender;
import randomTeamFC27.service.ratelimit.RateLimiter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private OtpCodeRepository otpCodeRepository;
    @Mock
    private OtpSender otpSender;
    @Mock
    private RateLimiter rateLimiter;

    private OtpService otpService;

    @BeforeEach
    void setUp() {
        otpService = new OtpService(otpCodeRepository, otpSender, rateLimiter);
        ReflectionTestUtils.setField(otpService, "codeTtlMinutes", 5L);
        ReflectionTestUtils.setField(otpService, "rateLimitMaxRequests", 3);
        ReflectionTestUtils.setField(otpService, "rateLimitWindowMinutes", 10L);
    }

    @Test
    void sendOtpGeneratesCodeAndDelegatesToSender() {
        when(rateLimiter.tryConsume(anyString(), anyInt(), anyLong())).thenReturn(true);

        otpService.sendOtp("user@example.com", OtpChannel.EMAIL);

        ArgumentCaptor<OtpCode> savedCaptor = ArgumentCaptor.forClass(OtpCode.class);
        verify(otpCodeRepository).save(savedCaptor.capture());
        assertEquals("user@example.com", savedCaptor.getValue().getDestination());
        assertEquals(OtpChannel.EMAIL, savedCaptor.getValue().getChannel());
        assertNotNull(savedCaptor.getValue().getCodeHash());

        verify(otpSender).send(anyString(), any(OtpChannel.class), anyString());
    }

    @Test
    void sendOtpRejectedWhenRateLimited() {
        when(rateLimiter.tryConsume(anyString(), anyInt(), anyLong())).thenReturn(false);

        assertThrows(ApiException.class, () -> otpService.sendOtp("user@example.com", OtpChannel.EMAIL));
        verify(otpSender, never()).send(anyString(), any(OtpChannel.class), anyString());
    }

    @Test
    void verifyOtpSucceedsWithCorrectCode() {
        String code = "123456";
        OtpCode stored = OtpCode.builder()
                .destination("user@example.com").channel(OtpChannel.EMAIL)
                .codeHash(HashUtil.sha256Hex(code))
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .build();
        when(otpCodeRepository.findFirstByDestinationAndChannelAndConsumedAtIsNullOrderByCreatedAtDesc(
                "user@example.com", OtpChannel.EMAIL)).thenReturn(Optional.of(stored));

        otpService.verifyOtp("user@example.com", OtpChannel.EMAIL, code);

        assertNotNull(stored.getConsumedAt());
        verify(otpCodeRepository).save(stored);
    }

    @Test
    void verifyOtpRejectsWrongCodeAndIncrementsAttempts() {
        OtpCode stored = OtpCode.builder()
                .destination("user@example.com").channel(OtpChannel.EMAIL)
                .codeHash(HashUtil.sha256Hex("123456"))
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .build();
        when(otpCodeRepository.findFirstByDestinationAndChannelAndConsumedAtIsNullOrderByCreatedAtDesc(
                "user@example.com", OtpChannel.EMAIL)).thenReturn(Optional.of(stored));

        assertThrows(ApiException.class, () -> otpService.verifyOtp("user@example.com", OtpChannel.EMAIL, "000000"));

        assertEquals(1, stored.getAttempts());
    }

    @Test
    void verifyOtpRejectsExpiredCode() {
        OtpCode stored = OtpCode.builder()
                .destination("user@example.com").channel(OtpChannel.EMAIL)
                .codeHash(HashUtil.sha256Hex("123456"))
                .expiresAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                .build();
        when(otpCodeRepository.findFirstByDestinationAndChannelAndConsumedAtIsNullOrderByCreatedAtDesc(
                "user@example.com", OtpChannel.EMAIL)).thenReturn(Optional.of(stored));

        assertThrows(ApiException.class, () -> otpService.verifyOtp("user@example.com", OtpChannel.EMAIL, "123456"));
    }

    @Test
    void verifyOtpRejectsAfterMaxAttempts() {
        OtpCode stored = OtpCode.builder()
                .destination("user@example.com").channel(OtpChannel.EMAIL)
                .codeHash(HashUtil.sha256Hex("123456"))
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .attempts(5)
                .build();
        when(otpCodeRepository.findFirstByDestinationAndChannelAndConsumedAtIsNullOrderByCreatedAtDesc(
                "user@example.com", OtpChannel.EMAIL)).thenReturn(Optional.of(stored));

        assertThrows(ApiException.class, () -> otpService.verifyOtp("user@example.com", OtpChannel.EMAIL, "123456"));
        verify(otpCodeRepository, never()).save(any());
    }

    @Test
    void verifyOtpRejectsWhenNothingPending() {
        when(otpCodeRepository.findFirstByDestinationAndChannelAndConsumedAtIsNullOrderByCreatedAtDesc(
                "user@example.com", OtpChannel.EMAIL)).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> otpService.verifyOtp("user@example.com", OtpChannel.EMAIL, "123456"));
    }
}
