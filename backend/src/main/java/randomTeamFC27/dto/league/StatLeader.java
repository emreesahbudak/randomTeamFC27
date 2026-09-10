package randomTeamFC27.dto.league;

/** Who leads a given stat (Golden Boot / Best Defense) and by how much. Null wherever the
 *  service returns it if the league has no recorded matches yet. */
public record StatLeader(Long playerId, String displayName, int value) {
}
