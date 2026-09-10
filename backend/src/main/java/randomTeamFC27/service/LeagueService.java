package randomTeamFC27.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import randomTeamFC27.entity.League;
import randomTeamFC27.entity.Season;
import randomTeamFC27.entity.User;
import randomTeamFC27.exception.ApiException;
import randomTeamFC27.repository.LeagueRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeagueService {

    private final LeagueRepository leagueRepository;
    private final SeasonService seasonService;

    @Transactional
    public League create(String name, User owner) {
        League league = leagueRepository.save(League.builder().name(name.trim()).owner(owner).build());
        seasonService.openSeason(league, "Sezon 1");
        log.info("User {} created league {} ({})", owner.getId(), league.getId(), league.getName());
        return league;
    }

    public List<League> listMine(User owner) {
        return leagueRepository.findAllByOwner(owner);
    }

    /**
     * Resolves a league and checks that {@code requester} owns it — there is no
     * membership/collaborator concept in this schema, only a single owner (see
     * {@link League#getOwner()}), so every league-scoped endpoint (players, matches,
     * standings, stats) routes through this before doing anything else.
     */
    public League getOwned(Long id, User requester) {
        League league = leagueRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Lig bulunamadı (id: " + id + ")"));
        if (!league.getOwner().getId().equals(requester.getId())) {
            throw ApiException.forbidden("Bu lig sana ait değil");
        }
        return league;
    }

    /**
     * "Reset league" — the prototype's "danger zone" action. Closes the current season and
     * opens a fresh one; matches recorded so far keep pointing at the now-closed season
     * (see {@link Season}) instead of being deleted, so they stay browsable under
     * "Geçmiş Ligler" (past leagues) — only the *current* standings/stats/match-list go
     * back to zero, since those are always scoped to whichever season is currently open.
     */
    @Transactional
    public Season resetMatches(Long id, User requester) {
        League league = getOwned(id, requester);
        Season newSeason = seasonService.closeCurrentAndOpenNext(league);
        log.info("User {} reset league {} (closed season, opened {})", requester.getId(), league.getId(), newSeason.getId());
        return newSeason;
    }
}
