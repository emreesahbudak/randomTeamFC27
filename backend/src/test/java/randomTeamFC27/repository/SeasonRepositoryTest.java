package randomTeamFC27.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import randomTeamFC27.entity.League;
import randomTeamFC27.entity.Season;
import randomTeamFC27.entity.User;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class SeasonRepositoryTest {

    @Autowired
    private SeasonRepository seasonRepository;
    @Autowired
    private LeagueRepository leagueRepository;
    @Autowired
    private UserRepository userRepository;

    private League league() {
        User owner = userRepository.save(User.builder().displayName("Emre").email("e@example.com").build());
        return leagueRepository.save(League.builder().name("Friday Nights").owner(owner).build());
    }

    @Test
    void findsTheOneOpenSeason() {
        League league = league();
        seasonRepository.save(Season.builder().league(league).name("Sezon 1").startedAt(Instant.now()).endedAt(Instant.now()).build());
        Season open = seasonRepository.save(Season.builder().league(league).name("Sezon 2").startedAt(Instant.now()).build());

        Optional<Season> found = seasonRepository.findFirstByLeagueAndEndedAtIsNull(league);

        assertTrue(found.isPresent());
        assertEquals(open.getId(), found.get().getId());
    }

    @Test
    void listsClosedSeasonsMostRecentlyClosedFirst() {
        League league = league();
        Season first = seasonRepository.save(Season.builder().league(league).name("Sezon 1").startedAt(Instant.now())
                .endedAt(Instant.now().minusSeconds(3600)).build());
        Season second = seasonRepository.save(Season.builder().league(league).name("Sezon 2").startedAt(Instant.now())
                .endedAt(Instant.now()).build());
        seasonRepository.save(Season.builder().league(league).name("Sezon 3").startedAt(Instant.now()).build()); // still open

        List<Season> closed = seasonRepository.findAllByLeagueAndEndedAtIsNotNullOrderByEndedAtDesc(league);

        assertEquals(2, closed.size());
        assertEquals(second.getId(), closed.get(0).getId());
        assertEquals(first.getId(), closed.get(1).getId());
    }
}
