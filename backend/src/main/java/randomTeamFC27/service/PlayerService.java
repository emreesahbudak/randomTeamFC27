package randomTeamFC27.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import randomTeamFC27.entity.League;
import randomTeamFC27.entity.Player;
import randomTeamFC27.entity.User;
import randomTeamFC27.exception.ApiException;
import randomTeamFC27.repository.PlayerRepository;
import randomTeamFC27.repository.UserRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final LeagueService leagueService;
    private final UserRepository userRepository;

    public Player add(Long leagueId, String displayName, Long linkedUserId, User requester) {
        League league = leagueService.getOwned(leagueId, requester);
        String trimmed = displayName.trim();
        if (playerRepository.findByLeagueAndDisplayName(league, trimmed).isPresent()) {
            throw ApiException.badRequest("\"" + trimmed + "\" adında bir oyuncu bu ligde zaten var");
        }

        Player.PlayerBuilder<?, ?> builder = Player.builder().league(league).displayName(trimmed);
        if (linkedUserId != null) {
            builder.user(userRepository.findById(linkedUserId)
                    .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı (id: " + linkedUserId + ")")));
        }
        Player player = playerRepository.save(builder.build());
        log.info("User {} added player {} ({}) to league {}", requester.getId(), player.getId(), trimmed, leagueId);
        return player;
    }

    public List<Player> list(Long leagueId, User requester, boolean includeInactive) {
        League league = leagueService.getOwned(leagueId, requester);
        return includeInactive ? playerRepository.findAllByLeague(league) : playerRepository.findAllByLeagueAndActiveTrue(league);
    }

    /** Soft-remove: keeps past matches valid (their player1/player2 FK is non-nullable) while
     *  dropping the player out of the active roster used for new matches. */
    public void remove(Long leagueId, Long playerId, User requester) {
        League league = leagueService.getOwned(leagueId, requester);
        Player player = playerRepository.findById(playerId)
                .filter(p -> p.getLeague().getId().equals(league.getId()))
                .orElseThrow(() -> ApiException.notFound("Oyuncu bulunamadı (id: " + playerId + ")"));
        player.setActive(false);
        playerRepository.save(player);
        log.info("User {} removed player {} from league {}", requester.getId(), playerId, leagueId);
    }
}
