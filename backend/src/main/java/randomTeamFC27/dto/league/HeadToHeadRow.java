package randomTeamFC27.dto.league;

/** Aggregate record between one pair of players who have played each other at least once. */
public record HeadToHeadRow(
        Long player1Id,
        String player1Name,
        Long player2Id,
        String player2Name,
        int player1Wins,
        int player2Wins,
        int draws) {
}
