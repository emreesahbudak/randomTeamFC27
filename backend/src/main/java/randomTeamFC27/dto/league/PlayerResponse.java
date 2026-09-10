package randomTeamFC27.dto.league;

import randomTeamFC27.entity.Player;

public record PlayerResponse(Long id, Long leagueId, String displayName, Long userId, boolean active) {

    public static PlayerResponse from(Player player) {
        return new PlayerResponse(
                player.getId(),
                player.getLeague().getId(),
                player.getDisplayName(),
                player.getUser() != null ? player.getUser().getId() : null,
                player.isActive());
    }
}
