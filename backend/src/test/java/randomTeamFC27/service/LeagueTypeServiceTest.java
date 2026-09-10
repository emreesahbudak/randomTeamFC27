package randomTeamFC27.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import randomTeamFC27.entity.LeagueType;
import randomTeamFC27.entity.Role;
import randomTeamFC27.entity.User;
import randomTeamFC27.exception.ApiException;
import randomTeamFC27.repository.LeagueTypeRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeagueTypeServiceTest {

    @Mock
    private LeagueTypeRepository leagueTypeRepository;

    private LeagueTypeService leagueTypeService;

    @BeforeEach
    void setUp() {
        leagueTypeService = new LeagueTypeService(leagueTypeRepository);
    }

    private User admin() {
        return User.builder().id(1L).displayName("Admin").email("admin@fc27.app").role(Role.ADMIN).build();
    }

    @Test
    void createTrimsNameAndSaves() {
        when(leagueTypeRepository.findByNameIgnoreCase("Premier League")).thenReturn(Optional.empty());
        when(leagueTypeRepository.save(any())).thenAnswer(inv -> {
            LeagueType lt = inv.getArgument(0);
            lt.setId(1L);
            return lt;
        });

        LeagueType created = leagueTypeService.create("  Premier League  ", admin());

        assertEquals("Premier League", created.getName());
    }

    @Test
    void createRejectsCaseInsensitiveDuplicate() {
        when(leagueTypeRepository.findByNameIgnoreCase("premier league"))
                .thenReturn(Optional.of(LeagueType.builder().id(1L).name("Premier League").build()));

        assertThrows(ApiException.class, () -> leagueTypeService.create("premier league", admin()));
        verify(leagueTypeRepository, never()).save(any());
    }

    @Test
    void getThrowsNotFoundWhenMissing() {
        when(leagueTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> leagueTypeService.get(99L));
    }
}
