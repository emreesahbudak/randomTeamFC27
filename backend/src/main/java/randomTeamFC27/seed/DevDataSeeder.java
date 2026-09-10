package randomTeamFC27.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import randomTeamFC27.entity.LeagueType;
import randomTeamFC27.entity.Role;
import randomTeamFC27.entity.Team;
import randomTeamFC27.entity.User;
import randomTeamFC27.repository.LeagueTypeRepository;
import randomTeamFC27.repository.TeamRepository;
import randomTeamFC27.repository.UserRepository;

import java.util.List;
import java.util.Map;

/**
 * Seeds dev-only data so later steps have something to work against without manual entry:
 * the 3 league type categories and 18 fictional teams from the FC27 UI prototype, and one
 * ADMIN test account. Never runs against a production profile.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private static final String EMBER_CIRCUIT = "Ember Circuit";
    private static final String VANTA_LEAGUE = "Vanta League";
    private static final String SOLSTICE_DIVISION = "Solstice Division";
    private static final String DEV_ADMIN_EMAIL = "admin@fc27.app";

    private final LeagueTypeRepository leagueTypeRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    @Override
    public void run(ApplicationArguments args) {
        seedAdminUser();
        seedTeams();
    }

    private void seedAdminUser() {
        if (userRepository.findByEmail(DEV_ADMIN_EMAIL).isPresent()) {
            log.info("Dev seed skipped — admin user {} already exists", DEV_ADMIN_EMAIL);
            return;
        }

        userRepository.save(User.builder()
                .displayName("Admin")
                .email(DEV_ADMIN_EMAIL)
                .role(Role.ADMIN)
                .build());
        log.info("Dev seed created admin user {} (role=ADMIN) — sign in via POST /api/auth/otp/email/send " +
                "with this email, then read the 6-digit code from this log (LoggingOtpSender)", DEV_ADMIN_EMAIL);
    }

    private void seedTeams() {
        if (teamRepository.count() > 0) {
            log.info("Dev seed skipped — teams table already has {} rows", teamRepository.count());
            return;
        }

        Map<String, LeagueType> leagueTypes = Map.of(
                EMBER_CIRCUIT, leagueType(EMBER_CIRCUIT),
                VANTA_LEAGUE, leagueType(VANTA_LEAGUE),
                SOLSTICE_DIVISION, leagueType(SOLSTICE_DIVISION));

        List<Team> teams = List.of(
                team("RCR", "Redcliff Rovers", "#d9482f", 4, leagueTypes.get(EMBER_CIRCUIT)),
                team("ASH", "Ashvale United", "#c7443f", 3, leagueTypes.get(EMBER_CIRCUIT)),
                team("SOL", "Solar FC", "#e8b34d", 5, leagueTypes.get(EMBER_CIRCUIT)),
                team("CIN", "Cinderpark Athletic", "#b5432e", 2, leagueTypes.get(EMBER_CIRCUIT)),
                team("KIL", "Kilnbrook City", "#a5361f", 3, leagueTypes.get(EMBER_CIRCUIT)),
                team("PYR", "Pyre Town", "#e0592e", 4, leagueTypes.get(EMBER_CIRCUIT)),
                team("VAN", "Vanta Wolves", "#3b3f6b", 5, leagueTypes.get(VANTA_LEAGUE)),
                team("NIT", "Nightshade FC", "#4a4680", 3, leagueTypes.get(VANTA_LEAGUE)),
                team("OBS", "Obsidian Rangers", "#4d5175", 4, leagueTypes.get(VANTA_LEAGUE)),
                team("RAV", "Ravenholt", "#33355c", 2, leagueTypes.get(VANTA_LEAGUE)),
                team("DUS", "Duskgate United", "#454a7a", 3, leagueTypes.get(VANTA_LEAGUE)),
                team("UMB", "Umbra City", "#5b5e94", 5, leagueTypes.get(VANTA_LEAGUE)),
                team("SLH", "Solstice Harbor", "#2f8f8a", 4, leagueTypes.get(SOLSTICE_DIVISION)),
                team("MER", "Meridian FC", "#2e7d6b", 3, leagueTypes.get(SOLSTICE_DIVISION)),
                team("TID", "Tidecrest Athletic", "#1f6e7a", 5, leagueTypes.get(SOLSTICE_DIVISION)),
                team("GLD", "Goldshore United", "#c99a3b", 2, leagueTypes.get(SOLSTICE_DIVISION)),
                team("AMB", "Amber Isle", "#d3a24a", 3, leagueTypes.get(SOLSTICE_DIVISION)),
                team("LUM", "Lumen City", "#4fa98f", 4, leagueTypes.get(SOLSTICE_DIVISION))
        );

        teamRepository.saveAll(teams);
        log.info("Dev seed inserted {} teams across {} leagues", teams.size(), leagueTypes.size());
    }

    private LeagueType leagueType(String name) {
        return leagueTypeRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> leagueTypeRepository.save(LeagueType.builder().name(name).build()));
    }

    private static Team team(String code, String name, String colorHex, int starLevel, LeagueType leagueType) {
        return Team.builder()
                .code(code)
                .name(name)
                .colorHex(colorHex)
                .starLevel(starLevel)
                .leagueType(leagueType)
                .active(true)
                .build();
    }
}
