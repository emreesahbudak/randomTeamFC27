package randomTeamFC27.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import randomTeamFC27.dto.team.TeamCreateRequest;
import randomTeamFC27.dto.team.TeamPatchRequest;
import randomTeamFC27.entity.LeagueType;
import randomTeamFC27.entity.Team;
import randomTeamFC27.entity.User;
import randomTeamFC27.exception.ApiException;
import randomTeamFC27.repository.TeamRepository;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final LeagueTypeService leagueTypeService;

    /**
     * @param starLevels optional filter, e.g. wheel's "2-5 stars" chips
     * @param leagueTypeId optional exact-match filter, e.g. wheel's league dropdown
     * @param includeInactive false for the public wheel (default); true for the admin
     *                        management view, which needs to see deactivated teams too
     *                        (otherwise a deactivated team simply vanishes from admin's
     *                        own list, with no way to confirm the deactivation happened)
     */
    public List<Team> list(List<Integer> starLevels, Long leagueTypeId, boolean includeInactive) {
        boolean hasStars = starLevels != null && !starLevels.isEmpty();
        boolean hasLeague = leagueTypeId != null;

        if (includeInactive) {
            if (hasStars && hasLeague) {
                return teamRepository.findAllByStarLevelInAndLeagueTypeId(starLevels, leagueTypeId);
            }
            if (hasStars) {
                return teamRepository.findAllByStarLevelIn(starLevels);
            }
            if (hasLeague) {
                return teamRepository.findAllByLeagueTypeId(leagueTypeId);
            }
            return teamRepository.findAll();
        }

        if (hasStars && hasLeague) {
            return teamRepository.findAllByActiveTrueAndStarLevelInAndLeagueTypeId(starLevels, leagueTypeId);
        }
        if (hasStars) {
            return teamRepository.findAllByActiveTrueAndStarLevelIn(starLevels);
        }
        if (hasLeague) {
            return teamRepository.findAllByActiveTrueAndLeagueTypeId(leagueTypeId);
        }
        return teamRepository.findAllByActiveTrue();
    }

    public Team get(Long id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Takım bulunamadı (id: " + id + ")"));
    }

    public Team create(TeamCreateRequest request, User admin) {
        LeagueType leagueType = leagueTypeService.get(request.leagueTypeId());
        Team team = Team.builder()
                .name(request.name())
                .code(request.code().toUpperCase(Locale.ROOT))
                .crestUrl(request.crestUrl())
                .colorHex(request.colorHex())
                .starLevel(request.starLevel())
                .leagueType(leagueType)
                .createdBy(admin)
                .active(true)
                .build();
        team = teamRepository.save(team);
        log.info("Admin {} created team {} ({})", admin.getId(), team.getId(), team.getName());
        return team;
    }

    public Team replace(Long id, TeamCreateRequest request, User admin) {
        Team team = get(id);
        LeagueType leagueType = leagueTypeService.get(request.leagueTypeId());
        team.setName(request.name());
        team.setCode(request.code().toUpperCase(Locale.ROOT));
        team.setCrestUrl(request.crestUrl());
        team.setColorHex(request.colorHex());
        team.setStarLevel(request.starLevel());
        team.setLeagueType(leagueType);
        team = teamRepository.save(team);
        log.info("Admin {} replaced team {}", admin.getId(), team.getId());
        return team;
    }

    public Team patch(Long id, TeamPatchRequest request, User admin) {
        Team team = get(id);
        if (request.name() != null) {
            team.setName(request.name());
        }
        if (request.code() != null) {
            team.setCode(request.code().toUpperCase(Locale.ROOT));
        }
        if (request.crestUrl() != null) {
            team.setCrestUrl(request.crestUrl());
        }
        if (request.colorHex() != null) {
            team.setColorHex(request.colorHex());
        }
        if (request.starLevel() != null) {
            team.setStarLevel(request.starLevel());
        }
        if (request.leagueTypeId() != null) {
            team.setLeagueType(leagueTypeService.get(request.leagueTypeId()));
        }
        team = teamRepository.save(team);
        log.info("Admin {} patched team {}", admin.getId(), team.getId());
        return team;
    }

    /**
     * Soft-deletes — {@link randomTeamFC27.entity.Match} rows reference teams with a
     * required, non-cascading foreign key, so hard-deleting a team ever used in a match
     * would either fail or corrupt league/match history. Deactivated teams just drop out
     * of {@link #list}.
     */
    public void deactivate(Long id, User admin) {
        Team team = get(id);
        team.setActive(false);
        teamRepository.save(team);
        log.info("Admin {} deactivated team {}", admin.getId(), team.getId());
    }
}
