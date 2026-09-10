package randomTeamFC27.dto.league;

import java.util.List;

/** {@code topScorer}/{@code bestDefense} are null if the league has no matches yet. */
public record LeagueStatsResponse(StatLeader topScorer, StatLeader bestDefense, List<HeadToHeadRow> headToHead) {
}
