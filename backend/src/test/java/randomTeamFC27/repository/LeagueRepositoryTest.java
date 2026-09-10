package randomTeamFC27.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import randomTeamFC27.entity.League;
import randomTeamFC27.entity.User;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class LeagueRepositoryTest {

    @Autowired
    private LeagueRepository leagueRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findsLeaguesByOwner() {
        User owner = userRepository.save(User.builder().displayName("Emre").email("e@example.com").build());
        User other = userRepository.save(User.builder().displayName("Kaya").email("k@example.com").build());
        leagueRepository.save(League.builder().name("Friday Nights").owner(owner).build());
        leagueRepository.save(League.builder().name("Other League").owner(other).build());

        List<League> result = leagueRepository.findAllByOwner(owner);

        assertEquals(1, result.size());
        assertEquals("Friday Nights", result.get(0).getName());
    }
}
