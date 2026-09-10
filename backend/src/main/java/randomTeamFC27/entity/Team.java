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
 * An FC27 team available in the wheel, managed via the admin CRUD panel. {@code leagueType}
 * is a category (e.g. "Ember Circuit") used for filtering — it is not the friend-group
 * {@link League} that tracks matches and standings.
 */
@Getter
@Setter
@Entity
@Table(name = "teams")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Team extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 5)
    private String code;

    @Column(name = "crest_url")
    private String crestUrl;

    @Column(name = "color_hex", length = 7)
    private String colorHex;

    @Column(name = "star_level", nullable = false)
    private int starLevel;

    // EAGER (unlike the rest of this entity's relations): every TeamResponse includes the
    // league type's id/name, and that mapping happens in the controller after the service's
    // transaction has already closed — LAZY here throws LazyInitializationException there.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "league_type_id", nullable = false)
    private LeagueType leagueType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
