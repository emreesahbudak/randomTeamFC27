package randomTeamFC27.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import randomTeamFC27.exception.ApiException;

/**
 * Verifies a Google Sign-In ID token by delegating to Google's own tokeninfo endpoint —
 * Google validates the signature and expiry server-side and simply errors on anything
 * invalid, so no JWKS/key-rotation handling is needed on our end.
 */
@Slf4j
@Service
public class GoogleAuthService {

    private final RestClient restClient;
    private final String clientId;

    public GoogleAuthService(RestClient.Builder restClientBuilder,
                              @Value("${app.google.client-id}") String clientId) {
        this.restClient = restClientBuilder.baseUrl("https://oauth2.googleapis.com").build();
        this.clientId = clientId;
    }

    public GoogleUserInfo verify(String idToken) {
        if (clientId == null || clientId.isBlank()) {
            throw ApiException.serviceUnavailable("Google ile giriş şu anda yapılandırılmamış");
        }

        TokenInfoResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/tokeninfo").queryParam("id_token", idToken).build())
                    .retrieve()
                    .body(TokenInfoResponse.class);
        } catch (RestClientException e) {
            log.warn("Google token verification failed: {}", e.getMessage());
            throw ApiException.unauthorized("Geçersiz Google token'ı");
        }

        if (response == null || !clientId.equals(response.aud())) {
            log.warn("Google token audience mismatch");
            throw ApiException.unauthorized("Geçersiz Google token'ı");
        }
        if (response.email() == null || response.email().isBlank()) {
            throw ApiException.unauthorized("Google hesabında e-posta bilgisi yok");
        }

        return new GoogleUserInfo(response.sub(), response.email(), response.name());
    }

    private record TokenInfoResponse(String sub, String email, String name, String aud) {
    }

    public record GoogleUserInfo(String googleId, String email, String name) {
    }
}
