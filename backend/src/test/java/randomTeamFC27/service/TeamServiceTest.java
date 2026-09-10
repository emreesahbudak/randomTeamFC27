package randomTeamFC27.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import randomTeamFC27.dto.team.TeamCreateRequest;
import randomTeamFC27.dto.team.TeamPatchRequest;
import randomTeamFC27.entity.LeagueType;
import randomTeamFC27.entity.Role;
import randomTeamFC27.entity.Team;
import randomTeamFC27.entity.User;
import randomTeamFC27.exception.ApiException;
import randomTeamFC27.repository.TeamRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    private TeamRepository teamRepository;
    @Mock
    private LeagueTypeService leagueTypeService;

    private TeamService teamService;

    @BeforeEach
    void setUp() {
        teamService = new TeamService(teamRepository, leagueTypeService);
    }

    private User admin() {
        return User.builder().id(1L).displayName("Admin").email("admin@fc27.app").role(Role.ADMIN).build();
    }

    private LeagueType leagueType(long id, String name) {
        return LeagueType.builder().id(id).name(name).build();
    }

    private Team team(long id) {
        return Team.builder().id(id).name("Solar FC").code("SOL").starLevel(5)
                .leagueType(leagueType(10L, "Ember Circuit")).active(true).build();
    }

    @Test
    void listWithNoFiltersReturnsAllActive() {
        when(teamRepository.findAllByActiveTrue()).thenReturn(List.of(team(1L)));

        List<Team> result = teamService.list(null, null, false);

        assertEquals(1, result.size());
        verify(teamRepository).findAllByActiveTrue();
    }

    @Test
    void listWithStarsOnlyUsesStarFilter() {
        when(teamRepository.findAllByActiveTrueAndStarLevelIn(List.of(4, 5))).thenReturn(List.of(team(1L)));

        teamService.list(List.of(4, 5), null, false);

        verify(teamRepository).findAllByActiveTrueAndStarLevelIn(List.of(4, 5));
        verify(teamRepository, never()).findAllByActiveTrue();
    }

    @Test
    void listWithLeagueTypeOnlyUsesLeagueFilter() {
        when(teamRepository.findAllByActiveTrueAndLeagueTypeId(10L)).thenReturn(List.of(team(1L)));

        teamService.list(null, 10L, false);

        verify(teamRepository).findAllByActiveTrueAndLeagueTypeId(10L);
    }

    @Test
    void listWithBothFiltersUsesCombinedQuery() {
        when(teamRepository.findAllByActiveTrueAndStarLevelInAndLeagueTypeId(List.of(5), 10L))
                .thenReturn(List.of(team(1L)));

        teamService.list(List.of(5), 10L, false);

        verify(teamRepository).findAllByActiveTrueAndStarLevelInAndLeagueTypeId(List.of(5), 10L);
    }

    @Test
    void listWithIncludeInactiveBypassesActiveFilter() {
        when(teamRepository.findAll()).thenReturn(List.of(team(1L)));

        teamService.list(null, null, true);

        verify(teamRepository).findAll();
        verify(teamRepository, never()).findAllByActiveTrue();
    }

    @Test
    void getThrowsNotFoundWhenMissing() {
        when(teamRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> teamService.get(99L));
    }

    @Test
    void createUppercasesCodeAndStampsCreator() {
        when(leagueTypeService.get(10L)).thenReturn(leagueType(10L, "Ember Circuit"));
        when(teamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        TeamCreateRequest request = new TeamCreateRequest("Solar FC", "sol", null, null, 5, 10L);

        Team created = teamService.create(request, admin());

        assertEquals("SOL", created.getCode());
        assertEquals(1L, created.getCreatedBy().getId());
        assertEquals(10L, created.getLeagueType().getId());
        assertTrueActive(created);
    }

    @Test
    void patchOnlyChangesProvidedFields() {
        Team existing = team(1L);
        existing.setCrestUrl("https://old.example.com/crest.png");
        when(teamRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(teamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TeamPatchRequest patch = new TeamPatchRequest(null, null, null, null, 3, null);
        Team result = teamService.patch(1L, patch, admin());

        assertEquals(3, result.getStarLevel());
        assertEquals("Solar FC", result.getName());
        assertEquals("https://old.example.com/crest.png", result.getCrestUrl());
        verify(leagueTypeService, never()).get(any());
    }

    @Test
    void patchChangesLeagueTypeWhenProvided() {
        Team existing = team(1L);
        when(teamRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(teamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(leagueTypeService.get(20L)).thenReturn(leagueType(20L, "Vanta League"));

        TeamPatchRequest patch = new TeamPatchRequest(null, null, null, null, null, 20L);
        Team result = teamService.patch(1L, patch, admin());

        assertEquals(20L, result.getLeagueType().getId());
    }

    @Test
    void deactivateSetsActiveFalseInsteadOfDeleting() {
        Team existing = team(1L);
        when(teamRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(teamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        teamService.deactivate(1L, admin());

        ArgumentCaptor<Team> captor = ArgumentCaptor.forClass(Team.class);
        verify(teamRepository).save(captor.capture());
        assertFalse(captor.getValue().isActive());
        verify(teamRepository, never()).delete(any());
        verify(teamRepository, never()).deleteById(any());
    }

    private void assertTrueActive(Team team) {
        org.junit.jupiter.api.Assertions.assertTrue(team.isActive());
    }
}
