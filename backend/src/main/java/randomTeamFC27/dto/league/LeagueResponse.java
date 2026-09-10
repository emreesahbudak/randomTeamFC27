package randomTeamFC27.dto.league;

import randomTeamFC27.entity.League;

import java.time.Instant;

public record LeagueResponse(Long id, String name, Long ownerId, Instant createdAt) {

    public static LeagueResponse from(League league) {
        return new LeagueResponse(league.getId(), league.getName(), league.getOwner().getId(), league.getCreatedAt());
    }
}
