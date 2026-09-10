package randomTeamFC27.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A participant within a {@link League}. {@code user} is nullable — a player can be added
 * to a league by display name only, without a registered account.
 */
@Getter
@Setter
@Entity
@Table(name = "players")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Player extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // Soft-removed players stay attached to their past matches (non-nullable FK, same
    // reasoning as Team.active) but drop out of the active roster shown for new matches.
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
