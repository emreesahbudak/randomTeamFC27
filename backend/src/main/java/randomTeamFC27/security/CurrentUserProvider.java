package randomTeamFC27.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import randomTeamFC27.entity.User;
import randomTeamFC27.exception.ApiException;
import randomTeamFC27.repository.UserRepository;

/**
 * Resolves the {@link User} behind the current request's JWT principal (a bare user id,
 * set by {@link JwtAuthenticationFilter}) into the full entity controllers/services need.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserProvider {

    private final UserRepository userRepository;

    public User require() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                : null;

        if (!(principal instanceof Long userId)) {
            throw ApiException.unauthorized("Giriş yapılmamış");
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Kullanıcı artık mevcut değil"));
    }
}
