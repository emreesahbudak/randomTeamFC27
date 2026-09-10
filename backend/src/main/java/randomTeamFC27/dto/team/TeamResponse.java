package randomTeamFC27.dto.team;

import randomTeamFC27.entity.Team;

import java.time.Instant;

public record TeamResponse(
        Long id,
        String name,
        String code,
        String crestUrl,
        String colorHex,
        int starLevel,
        Long leagueTypeId,
        String leagueTypeName,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static TeamResponse from(Team team) {
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getCode(),
                team.getCrestUrl(),
                team.getColorHex(),
                team.getStarLevel(),
                team.getLeagueType().getId(),
                team.getLeagueType().getName(),
                team.isActive(),
                team.getCreatedAt(),
                team.getUpdatedAt());
    }
}
