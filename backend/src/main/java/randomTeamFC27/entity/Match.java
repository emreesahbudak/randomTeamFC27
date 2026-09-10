package randomTeamFC27.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * A recorded match result within a {@link League}. A row only exists once a final score has
 * been registered — the pre-submission "live registration" state lives client-side only.
 * League standings are always computed from these rows; they are never edited directly.
 */
@Getter
@Setter
@Entity
@Table(name = "matches")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Match extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    // Which run of the league this match belongs to — standings/stats/match-list are always
    // scoped to a Season (the current one, or a past one under "Geçmiş Ligler"), never to
    // the whole League directly, so a "reset" (closing the season) cleanly separates eras
    // without deleting anything. See Season's own javadoc.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player1_id", nullable = false)
    private Player player1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player2_id", nullable = false)
    private Player player2;

    // EAGER, not the LAZY-by-default: a match is never displayed without its team names
    // (same reasoning as Team.leagueType) — avoids a LazyInitializationException in
    // MatchResponse.from() once the service method that loaded this Match has returned.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "team1_id", nullable = false)
    private Team team1;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "team2_id", nullable = false)
    private Team team2;

    @Column(name = "score1", nullable = false)
    private int score1;

    @Column(name = "score2", nullable = false)
    private int score2;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;
}
