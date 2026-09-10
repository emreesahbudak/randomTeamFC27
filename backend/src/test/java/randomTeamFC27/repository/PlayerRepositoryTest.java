package randomTeamFC27.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import randomTeamFC27.entity.League;
import randomTeamFC27.entity.Player;
import randomTeamFC27.entity.User;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class PlayerRepositoryTest {

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private LeagueRepository leagueRepository;

    @Autowired
    private UserRepository userRepository;

    private League createLeague() {
        User owner = userRepository.save(User.builder().displayName("Emre").email("e@example.com").build());
        return leagueRepository.save(League.builder().name("Friday Nights").owner(owner).build());
    }

    @Test
    void addsPlayerByDisplayNameWithoutUserAccount() {
        League league = createLeague();

        Player saved = playerRepository.save(Player.builder()
                .league(league).displayName("Mehmet").build());

        assertEquals("Mehmet", saved.getDisplayName());
        assertEquals(league.getId(), saved.getLeague().getId());
    }

    @Test
    void findsPlayerByLeagueAndDisplayName() {
        League league = createLeague();
        playerRepository.save(Player.builder().league(league).displayName("Ahmet").build());

        Optional<Player> found = playerRepository.findByLeagueAndDisplayName(league, "Ahmet");

        assertTrue(found.isPresent());
    }

    @Test
    void listsAllPlayersInLeague() {
        League league = createLeague();
        playerRepository.save(Player.builder().league(league).displayName("Ahmet").build());
        playerRepository.save(Player.builder().league(league).displayName("Mehmet").build());

        List<Player> players = playerRepository.findAllByLeague(league);

        assertEquals(2, players.size());
    }

    @Test
    void rejectsDuplicateDisplayNameWithinSameLeague() {
        League league = createLeague();
        playerRepository.saveAndFlush(Player.builder().league(league).displayName("Ahmet").build());

        assertThrows(DataIntegrityViolationException.class, () ->
                playerRepository.saveAndFlush(Player.builder().league(league).displayName("Ahmet").build()));
    }
}
