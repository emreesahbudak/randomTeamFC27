package randomTeamFC27.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import randomTeamFC27.dto.league.MatchCreateRequest;
import randomTeamFC27.dto.league.MatchResponse;
import randomTeamFC27.entity.League;
import randomTeamFC27.entity.Match;
import randomTeamFC27.entity.Player;
import randomTeamFC27.entity.Season;
import randomTeamFC27.entity.Team;
import randomTeamFC27.entity.User;
import randomTeamFC27.exception.ApiException;
import randomTeamFC27.repository.MatchRepository;
import randomTeamFC27.repository.PlayerRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final LeagueService leagueService;
    private final SeasonService seasonService;
    private final TeamService teamService;

    /** Always records into the league's current (open) season — there is no way to add a
     *  match to a past, closed season. */
    public MatchResponse record(Long leagueId, MatchCreateRequest request, User requester) {
        League league = leagueService.getOwned(leagueId, requester);
        Season season = seasonService.currentSeason(league);

        if (request.player1Id().equals(request.player2Id())) {
            throw ApiException.badRequest("Bir oyuncu kendisiyle eşleşemez");
        }
        Player player1 = requirePlayerInLeague(league, request.player1Id());
        Player player2 = requirePlayerInLeague(league, request.player2Id());
        Team team1 = teamService.get(request.team1Id());
        Team team2 = teamService.get(request.team2Id());

        Match match = matchRepository.save(Match.builder()
                .league(league)
                .season(season)
                .player1(player1)
                .player2(player2)
                .team1(team1)
                .team2(team2)
                .score1(request.score1())
                .score2(request.score2())
                .recordedAt(Instant.now())
                .recordedBy(requester)
                .build());
        log.info("User {} recorded match {} in league {} (season {}): {} {}-{} {}",
                requester.getId(), match.getId(), leagueId, season.getId(),
                player1.getDisplayName(), match.getScore1(), match.getScore2(), player2.getDisplayName());
        return MatchResponse.from(match, player1.getDisplayName(), player2.getDisplayName());
    }

    public List<MatchResponse> list(Long leagueId, User requester) {
        League league = leagueService.getOwned(leagueId, requester);
        return listForSeason(league, seasonService.currentSeason(league));
    }

    public List<MatchResponse> listForPastSeason(Long leagueId, Long seasonId, User requester) {
        League league = leagueService.getOwned(leagueId, requester);
        return listForSeason(league, seasonService.getPastSeason(league, seasonId));
    }

    private List<MatchResponse> listForSeason(League league, Season season) {
        List<Match> matches = matchRepository.findAllBySeasonOrderByRecordedAtDesc(season);
        // Player names are resolved from a single batch-loaded, non-proxy map rather than
        // match.getPlayer1()/getPlayer2() (LAZY relations) — see MatchResponse.from()'s note.
        Map<Long, String> namesByPlayerId = playerRepository.findAllByLeague(league).stream()
                .collect(Collectors.toMap(Player::getId, Player::getDisplayName));
        return matches.stream()
                .map(match -> MatchResponse.from(match,
                        namesByPlayerId.get(match.getPlayer1().getId()),
                        namesByPlayerId.get(match.getPlayer2().getId())))
                .toList();
    }

    private Player requirePlayerInLeague(League league, Long playerId) {
        return playerRepository.findById(playerId)
                .filter(p -> p.getLeague().getId().equals(league.getId()))
                .orElseThrow(() -> ApiException.notFound("Oyuncu bulunamadı (id: " + playerId + ")"));
    }
}
