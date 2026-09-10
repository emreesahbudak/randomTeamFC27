package randomTeamFC27.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A team category (e.g. "Premier League", "Ember Circuit") teams are filed under for
 * wheel filtering. A normalized lookup table rather than a free-text column or an enum:
 * an enum can't grow without a redeploy, and free text lets two admins create "Premier
 * League" and "Premier Lig" as silently different categories via a typo.
 */
@Getter
@Setter
@Entity
@Table(name = "league_types")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LeagueType extends BaseEntity {

    @Column(nullable = false, unique = true, length = 120)
    private String name;
}
