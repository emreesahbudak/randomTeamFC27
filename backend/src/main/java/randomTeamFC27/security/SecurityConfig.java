package randomTeamFC27.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless JWT security: no sessions, no Spring Security {@code UserDetailsService}
 * (there are no passwords in this app — see {@link randomTeamFC27.RandomTeamFc27Application}'s
 * exclusion of {@code UserDetailsServiceAutoConfiguration}). CSRF is disabled because the
 * only cookie in play (the refresh token) is {@code SameSite=Lax} and every state-changing
 * request also requires a bearer access token that a cross-site form can't forge.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Default Spring Security behavior returns 403 for an anonymous request
                // hitting an authenticated endpoint; 401 is the correct code for "you're not
                // logged in at all" as opposed to "you're logged in but not allowed".
                .exceptionHandling(handling -> handling.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Logout needs a valid access token to know who is logging out —
                        // everything else here is how you'd *get* a token in the first place.
                        .requestMatchers("/api/auth/google", "/api/auth/otp/**", "/api/auth/refresh").permitAll()
                        .requestMatchers("/api/auth/logout").authenticated()
                        // Anyone (including guests, who never authenticate at all) can browse
                        // the team pool for the wheel; only admins may change it.
                        .requestMatchers(HttpMethod.GET, "/api/teams/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/teams/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/teams/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/teams/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/league-types/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/league-types/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/teams/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
