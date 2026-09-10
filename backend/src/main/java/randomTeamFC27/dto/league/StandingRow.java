package randomTeamFC27.dto.league;

/** One player's row in a league table — always derived from recorded matches, never stored. */
public record StandingRow(
        Long playerId,
        String displayName,
        int played,
        int won,
        int drawn,
        int lost,
        int goalsFor,
        int goalsAgainst,
        int goalDifference,
        int points) {
}
