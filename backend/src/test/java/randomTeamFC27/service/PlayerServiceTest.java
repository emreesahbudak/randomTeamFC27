package randomTeamFC27.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import randomTeamFC27.entity.League;
import randomTeamFC27.entity.Player;
import randomTeamFC27.entity.Role;
import randomTeamFC27.entity.User;
import randomTeamFC27.exception.ApiException;
import randomTeamFC27.repository.PlayerRepository;
import randomTeamFC27.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private LeagueService leagueService;
    @Mock
    private UserRepository userRepository;

    private PlayerService playerService;

    @BeforeEach
    void setUp() {
        playerService = new PlayerService(playerRepository, leagueService, userRepository);
    }

    private User owner() {
        return User.builder().id(1L).displayName("Owner").role(Role.USER).build();
    }

    private League league() {
        return League.builder().id(1L).name("Friday Nights").owner(owner()).build();
    }

    @Test
    void addRejectsDuplicateDisplayNameInSameLeague() {
        League league = league();
        when(leagueService.getOwned(1L, owner())).thenReturn(league);
        when(playerRepository.findByLeagueAndDisplayName(league, "Ahmet"))
                .thenReturn(Optional.of(Player.builder().id(5L).league(league).displayName("Ahmet").build()));

        assertThrows(ApiException.class, () -> playerService.add(1L, "Ahmet", null, owner()));
    }

    @Test
    void addWithoutUserIdCreatesUnlinkedPlayer() {
        League league = league();
        when(leagueService.getOwned(1L, owner())).thenReturn(league);
        when(playerRepository.findByLeagueAndDisplayName(league, "Ahmet")).thenReturn(Optional.empty());
        when(playerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Player player = playerService.add(1L, "Ahmet", null, owner());

        assertEquals("Ahmet", player.getDisplayName());
        assertEquals(null, player.getUser());
        verify(userRepository, org.mockito.Mockito.never()).findById(any());
    }

    @Test
    void removeSoftDeletesInsteadOfHardDeleting() {
        League league = league();
        Player player = Player.builder().id(5L).league(league).displayName("Ahmet").active(true).build();
        when(leagueService.getOwned(1L, owner())).thenReturn(league);
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));
        when(playerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        playerService.remove(1L, 5L, owner());

        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(captor.capture());
        assertFalse(captor.getValue().isActive());
        verify(playerRepository, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    void removeRejectsPlayerFromAnotherLeague() {
        League league = league();
        League otherLeague = League.builder().id(2L).name("Other").owner(owner()).build();
        Player player = Player.builder().id(5L).league(otherLeague).displayName("Ahmet").active(true).build();
        when(leagueService.getOwned(1L, owner())).thenReturn(league);
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));

        assertThrows(ApiException.class, () -> playerService.remove(1L, 5L, owner()));
    }

    @Test
    void listDefaultsToActiveOnly() {
        League league = league();
        when(leagueService.getOwned(1L, owner())).thenReturn(league);
        when(playerRepository.findAllByLeagueAndActiveTrue(league)).thenReturn(List.of());

        playerService.list(1L, owner(), false);

        verify(playerRepository).findAllByLeagueAndActiveTrue(league);
        verify(playerRepository, org.mockito.Mockito.never()).findAllByLeague(any());
    }
}
