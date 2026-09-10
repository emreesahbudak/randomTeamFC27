package randomTeamFC27.dto.league;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MatchCreateRequest(
        @NotNull Long player1Id,
        @NotNull Long player2Id,
        @NotNull Long team1Id,
        @NotNull Long team2Id,
        @NotNull @Min(0) Integer score1,
        @NotNull @Min(0) Integer score2) {
}
