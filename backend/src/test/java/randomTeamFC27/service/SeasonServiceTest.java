package randomTeamFC27.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import randomTeamFC27.entity.League;
import randomTeamFC27.entity.Role;
import randomTeamFC27.entity.Season;
import randomTeamFC27.entity.User;
import randomTeamFC27.exception.ApiException;
import randomTeamFC27.repository.SeasonRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeasonServiceTest {

    @Mock
    private SeasonRepository seasonRepository;

    private SeasonService seasonService;

    @BeforeEach
    void setUp() {
        seasonService = new SeasonService(seasonRepository);
    }

    private League league() {
        User owner = User.builder().id(1L).displayName("Owner").role(Role.USER).build();
        return League.builder().id(1L).name("Friday Nights").owner(owner).build();
    }

    @Test
    void openSeasonSavesWithStartedAtStamped() {
        League league = league();
        when(seasonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Season season = seasonService.openSeason(league, "Sezon 1");

        assertEquals("Sezon 1", season.getName());
        assertNotNull(season.getStartedAt());
        assertEquals(league, season.getLeague());
    }

    @Test
    void currentSeasonThrowsIfSomehowNoneOpen() {
        League league = league();
        when(seasonRepository.findFirstByLeagueAndEndedAtIsNull(league)).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> seasonService.currentSeason(league));
    }

    @Test
    void getPastSeasonRejectsStillOpenSeason() {
        League league = league();
        Season open = Season.builder().id(5L).league(league).name("Sezon 1").startedAt(Instant.now()).build();
        when(seasonRepository.findById(5L)).thenReturn(Optional.of(open));

        assertThrows(ApiException.class, () -> seasonService.getPastSeason(league, 5L));
    }

    @Test
    void getPastSeasonRejectsSeasonFromAnotherLeague() {
        League league = league();
        League otherLeague = League.builder().id(2L).name("Other").owner(league.getOwner()).build();
        Season foreign = Season.builder().id(5L).league(otherLeague).name("Sezon 1").startedAt(Instant.now()).endedAt(Instant.now()).build();
        when(seasonRepository.findById(5L)).thenReturn(Optional.of(foreign));

        assertThrows(ApiException.class, () -> seasonService.getPastSeason(league, 5L));
    }

    @Test
    void closeCurrentAndOpenNextStampsEndedAtAndNumbersNextSeason() {
        League league = league();
        Season current = Season.builder().id(1L).league(league).name("Sezon 1").startedAt(Instant.now()).build();
        when(seasonRepository.findFirstByLeagueAndEndedAtIsNull(league)).thenReturn(Optional.of(current));
        when(seasonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(seasonRepository.findAllByLeagueAndEndedAtIsNotNullOrderByEndedAtDesc(league)).thenReturn(List.of(current));

        Season next = seasonService.closeCurrentAndOpenNext(league);

        assertNotNull(current.getEndedAt());
        assertEquals("Sezon 2", next.getName());

        ArgumentCaptor<Season> captor = ArgumentCaptor.forClass(Season.class);
        verify(seasonRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals(current.getId(), captor.getAllValues().get(0).getId());
    }
}
