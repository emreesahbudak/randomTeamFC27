package randomTeamFC27.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Reads {@code Authorization: Bearer <token>}, and on a valid access token, sets a
 * {@link randomTeamFC27.entity.User} id as the authenticated principal with a
 * {@code ROLE_<role>} authority. No token, or an invalid one, simply leaves the request
 * unauthenticated — {@link SecurityConfig} decides whether that's allowed for the path.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        extractBearerToken(request)
                .flatMap(jwtService::parse)
                .ifPresent(claims -> {
                    var authority = new SimpleGrantedAuthority("ROLE_" + claims.role().name());
                    var authentication = new UsernamePasswordAuthenticationToken(
                            claims.userId(), null, List.of(authority));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });

        filterChain.doFilter(request, response);
    }

    private Optional<String> extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return Optional.of(header.substring("Bearer ".length()));
        }
        return Optional.empty();
    }
}
