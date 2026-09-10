package randomTeamFC27.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import randomTeamFC27.entity.LeagueType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class LeagueTypeRepositoryTest {

    @Autowired
    private LeagueTypeRepository leagueTypeRepository;

    @Test
    void findsByNameCaseInsensitively() {
        leagueTypeRepository.save(LeagueType.builder().name("Premier League").build());

        assertTrue(leagueTypeRepository.findByNameIgnoreCase("premier league").isPresent());
        assertTrue(leagueTypeRepository.findByNameIgnoreCase("PREMIER LEAGUE").isPresent());
        assertFalse(leagueTypeRepository.findByNameIgnoreCase("La Liga").isPresent());
    }

    @Test
    void listsAlphabetically() {
        leagueTypeRepository.save(LeagueType.builder().name("Vanta League").build());
        leagueTypeRepository.save(LeagueType.builder().name("Ember Circuit").build());

        List<LeagueType> all = leagueTypeRepository.findAllByOrderByNameAsc();

        assertEquals(List.of("Ember Circuit", "Vanta League"),
                all.stream().map(LeagueType::getName).toList());
    }

    @Test
    void rejectsDuplicateNameAtDbLevel() {
        leagueTypeRepository.saveAndFlush(LeagueType.builder().name("Premier League").build());

        assertThrows(DataIntegrityViolationException.class, () ->
                leagueTypeRepository.saveAndFlush(LeagueType.builder().name("Premier League").build()));
    }
}
