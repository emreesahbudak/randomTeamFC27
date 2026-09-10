package randomTeamFC27.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import randomTeamFC27.entity.League;
import randomTeamFC27.entity.Role;
import randomTeamFC27.entity.Season;
import randomTeamFC27.entity.User;
import randomTeamFC27.exception.ApiException;
import randomTeamFC27.repository.LeagueRepository;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeagueServiceTest {

    @Mock
    private LeagueRepository leagueRepository;
    @Mock
    private SeasonService seasonService;

    private LeagueService leagueService;

    @BeforeEach
    void setUp() {
        leagueService = new LeagueService(leagueRepository, seasonService);
    }

    private User user(long id) {
        return User.builder().id(id).displayName("User " + id).role(Role.USER).build();
    }

    @Test
    void createSavesLeagueWithOwnerAndOpensFirstSeason() {
        User owner = user(1L);
        when(leagueRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        League league = leagueService.create("  Friday Nights  ", owner);

        assertEquals("Friday Nights", league.getName());
        assertEquals(1L, league.getOwner().getId());
        verify(seasonService).openSeason(league, "Sezon 1");
    }

    @Test
    void getOwnedThrowsNotFoundWhenMissing() {
        when(leagueRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> leagueService.getOwned(99L, user(1L)));
    }

    @Test
    void getOwnedThrowsForbiddenWhenNotOwner() {
        League league = League.builder().id(1L).name("Friday Nights").owner(user(1L)).build();
        when(leagueRepository.findById(1L)).thenReturn(Optional.of(league));

        ApiException ex = assertThrows(ApiException.class, () -> leagueService.getOwned(1L, user(2L)));
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    void resetMatchesClosesCurrentSeasonAndOpensNextForOwnedLeague() {
        User owner = user(1L);
        League league = League.builder().id(1L).name("Friday Nights").owner(owner).build();
        Season next = Season.builder().id(2L).league(league).name("Sezon 2").startedAt(Instant.now()).build();
        when(leagueRepository.findById(1L)).thenReturn(Optional.of(league));
        when(seasonService.closeCurrentAndOpenNext(league)).thenReturn(next);

        Season result = leagueService.resetMatches(1L, owner);

        assertEquals(2L, result.getId());
        verify(seasonService).closeCurrentAndOpenNext(league);
    }

    @Test
    void resetMatchesRejectsNonOwner() {
        League league = League.builder().id(1L).name("Friday Nights").owner(user(1L)).build();
        when(leagueRepository.findById(1L)).thenReturn(Optional.of(league));

        assertThrows(ApiException.class, () -> leagueService.resetMatches(1L, user(2L)));
        verify(seasonService, never()).closeCurrentAndOpenNext(org.mockito.ArgumentMatchers.any());
    }
}
