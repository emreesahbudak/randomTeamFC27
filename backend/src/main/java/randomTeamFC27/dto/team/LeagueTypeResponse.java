package randomTeamFC27.dto.team;

import randomTeamFC27.entity.LeagueType;

public record LeagueTypeResponse(Long id, String name) {

    public static LeagueTypeResponse from(LeagueType leagueType) {
        return new LeagueTypeResponse(leagueType.getId(), leagueType.getName());
    }
}
