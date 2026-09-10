package randomTeamFC27.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import randomTeamFC27.entity.OtpChannel;
import randomTeamFC27.service.GoogleAuthService;
import randomTeamFC27.service.otp.OtpSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OtpSender otpSender;

    @MockitoBean
    private GoogleAuthService googleAuthService;

    private String sendAndCaptureEmailOtp(String email) throws Exception {
        mockMvc.perform(post("/api/auth/otp/email/send")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EmailSend(email))))
                .andExpect(status().isAccepted());

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(otpSender).send(org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.eq(OtpChannel.EMAIL), codeCaptor.capture());
        return codeCaptor.getValue();
    }

    @Test
    void emailOtpSendThenVerifyIssuesTokens() throws Exception {
        String email = "emre+" + System.nanoTime() + "@example.com";
        String code = sendAndCaptureEmailOtp(email);

        mockMvc.perform(post("/api/auth/otp/email/verify")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EmailVerify(email, code, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email));
    }

    @Test
    void emailOtpVerifyWithWrongCodeIsRejected() throws Exception {
        String email = "emre+" + System.nanoTime() + "@example.com";
        sendAndCaptureEmailOtp(email);

        mockMvc.perform(post("/api/auth/otp/email/verify")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EmailVerify(email, "000000", false))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rememberMeSetsRefreshCookie() throws Exception {
        String email = "emre+" + System.nanoTime() + "@example.com";
        String code = sendAndCaptureEmailOtp(email);

        MvcResult result = mockMvc.perform(post("/api/auth/otp/email/verify")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EmailVerify(email, code, true))))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = result.getResponse().getCookie("refresh_token");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
    }

    @Test
    void withoutRememberMeNoCookieIsSet() throws Exception {
        String email = "emre+" + System.nanoTime() + "@example.com";
        String code = sendAndCaptureEmailOtp(email);

        MvcResult result = mockMvc.perform(post("/api/auth/otp/email/verify")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EmailVerify(email, code, false))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getCookie("refresh_token")).isNull();
    }

    @Test
    void refreshRotatesTokenAndOldTokenBecomesInvalid() throws Exception {
        String email = "emre+" + System.nanoTime() + "@example.com";
        String code = sendAndCaptureEmailOtp(email);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/otp/email/verify")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EmailVerify(email, code, false))))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RefreshBody(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // Reusing the same (now-rotated-away) refresh token must fail.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RefreshBody(refreshToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sendOtpIsRateLimitedAfterConfiguredMaxRequests() throws Exception {
        String email = "emre+" + System.nanoTime() + "@example.com";

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/otp/email/send")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(new EmailSend(email))))
                    .andExpect(status().isAccepted());
        }

        mockMvc.perform(post("/api/auth/otp/email/send")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EmailSend(email))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void logoutWithoutAuthTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshTokenSoItCannotBeUsedAgain() throws Exception {
        String email = "emre+" + System.nanoTime() + "@example.com";
        String code = sendAndCaptureEmailOtp(email);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/otp/email/verify")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new EmailVerify(email, code, false))))
                .andExpect(status().isOk())
                .andReturn();

        var json = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = json.get("accessToken").asText();
        String refreshToken = json.get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RefreshBody(refreshToken))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RefreshBody(refreshToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void googleLoginCreatesUserAndIssuesTokens() throws Exception {
        when(googleAuthService.verify(anyString()))
                .thenReturn(new GoogleAuthService.GoogleUserInfo("google-sub-123", "googleuser+" + System.nanoTime() + "@example.com", "Google User"));

        mockMvc.perform(post("/api/auth/google")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new GoogleLogin("some-id-token", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.displayName").value("Google User"));
    }

    private record EmailSend(String email) {
    }

    private record EmailVerify(String email, String code, boolean rememberMe) {
    }

    private record RefreshBody(String refreshToken) {
    }

    private record GoogleLogin(String idToken, boolean rememberMe) {
    }
}
