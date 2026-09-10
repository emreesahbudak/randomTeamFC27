package randomTeamFC27.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import randomTeamFC27.entity.LeagueType;

import java.util.List;
import java.util.Optional;

public interface LeagueTypeRepository extends JpaRepository<LeagueType, Long> {

    List<LeagueType> findAllByOrderByNameAsc();

    /**
     * Deliberately NOT a derived {@code UPPER(name)=UPPER(?)} query: both H2's and
     * Postgres' {@code UPPER()} fall back to the JVM/DB default locale, and under
     * Turkish locale (this project's dev environment) lowercase "i" upper-cases to "İ"
     * (dotted), not ASCII "I" — silently breaking exactly this kind of match. Java's
     * {@code String.equalsIgnoreCase} does locale-independent case folding, so we do the
     * comparison in application code instead. The table is small (a handful of league
     * categories), so scanning it all is cheap.
     */
    default Optional<LeagueType> findByNameIgnoreCase(String name) {
        return findAllByOrderByNameAsc().stream()
                .filter(leagueType -> leagueType.getName().equalsIgnoreCase(name))
                .findFirst();
    }
}
