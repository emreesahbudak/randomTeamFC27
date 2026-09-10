package randomTeamFC27.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import randomTeamFC27.entity.League;
import randomTeamFC27.entity.Season;
import randomTeamFC27.exception.ApiException;
import randomTeamFC27.repository.SeasonRepository;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonService {

    private final SeasonRepository seasonRepository;

    /** Every league has exactly one open (endedAt == null) season at all times — created
     *  alongside the league itself in {@link LeagueService#create}. */
    public Season openSeason(League league, String name) {
        return seasonRepository.save(Season.builder()
                .league(league)
                .name(name)
                .startedAt(Instant.now())
                .build());
    }

    public Season currentSeason(League league) {
        return seasonRepository.findFirstByLeagueAndEndedAtIsNull(league)
                .orElseThrow(() -> ApiException.badRequest("Ligin açık bir sezonu yok — bu normalde hiç olmamalı"));
    }

    public List<Season> pastSeasons(League league) {
        return seasonRepository.findAllByLeagueAndEndedAtIsNotNullOrderByEndedAtDesc(league);
    }

    public Season getPastSeason(League league, Long seasonId) {
        Season season = seasonRepository.findById(seasonId)
                .filter(s -> s.getLeague().getId().equals(league.getId()))
                .orElseThrow(() -> ApiException.notFound("Sezon bulunamadı (id: " + seasonId + ")"));
        if (season.getEndedAt() == null) {
            throw ApiException.badRequest("Bu sezon hâlâ açık — geçmiş ligler yerine güncel puan durumunu kullan");
        }
        return season;
    }

    /** "Reset league": closes the current season and opens a new one, numbered one past the
     *  count of seasons this league has ever had — matches already recorded keep pointing at
     *  the now-closed season, so nothing is lost (see {@link randomTeamFC27.entity.Season}). */
    public Season closeCurrentAndOpenNext(League league) {
        Season current = currentSeason(league);
        current.setEndedAt(Instant.now());
        seasonRepository.save(current);
        int nextNumber = pastSeasons(league).size() + 1;
        Season next = openSeason(league, "Sezon " + nextNumber);
        log.info("Closed season {} and opened season {} for league {}", current.getId(), next.getId(), league.getId());
        return next;
    }
}
