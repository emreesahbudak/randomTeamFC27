package randomTeamFC27.dto.league;

import randomTeamFC27.entity.Match;

import java.time.Instant;

public record MatchResponse(
        Long id,
        Long leagueId,
        Long player1Id,
        String player1Name,
        Long player2Id,
        String player2Name,
        Long team1Id,
        String team1Name,
        Long team2Id,
        String team2Name,
        int score1,
        int score2,
        Instant recordedAt) {

    /**
     * {@code player1Name}/{@code player2Name} are passed in rather than read off
     * {@code match.getPlayer1()/getPlayer2()} — those relations are LAZY, and the caller
     * (MatchService) already has the league's full, non-proxy {@link randomTeamFC27.entity.Player}
     * list loaded for other purposes, so resolving names through that avoids a
     * LazyInitializationException without having to widen Match.player1/player2 to EAGER.
     */
    public static MatchResponse from(Match match, String player1Name, String player2Name) {
        return new MatchResponse(
                match.getId(),
                match.getLeague().getId(),
                match.getPlayer1().getId(),
                player1Name,
                match.getPlayer2().getId(),
                player2Name,
                match.getTeam1().getId(),
                match.getTeam1().getName(),
                match.getTeam2().getId(),
                match.getTeam2().getName(),
                match.getScore1(),
                match.getScore2(),
                match.getRecordedAt());
    }
}
