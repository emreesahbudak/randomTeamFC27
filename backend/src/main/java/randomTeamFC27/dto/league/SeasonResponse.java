package randomTeamFC27.dto.league;

import randomTeamFC27.entity.Season;

import java.time.Instant;

public record SeasonResponse(Long id, String name, Instant startedAt, Instant endedAt) {

    public static SeasonResponse from(Season season) {
        return new SeasonResponse(season.getId(), season.getName(), season.getStartedAt(), season.getEndedAt());
    }
}
