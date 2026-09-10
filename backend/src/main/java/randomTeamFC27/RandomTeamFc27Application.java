package randomTeamFC27;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * Excludes {@link UserDetailsServiceAutoConfiguration} — this app has no passwords
 * (auth is Google/OTP-only, see {@link randomTeamFC27.security.SecurityConfig}), so the
 * default in-memory user/generated-password setup would just be dead weight and log noise.
 */
@Slf4j
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class RandomTeamFc27Application {

	public static void main(String[] args) {
		SpringApplication.run(RandomTeamFc27Application.class, args);
		log.info("FC27 backend started");
	}

}
