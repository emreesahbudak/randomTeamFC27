package randomTeamFC27.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import randomTeamFC27.entity.LeagueType;
import randomTeamFC27.entity.User;
import randomTeamFC27.exception.ApiException;
import randomTeamFC27.repository.LeagueTypeRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeagueTypeService {

    private final LeagueTypeRepository leagueTypeRepository;

    public List<LeagueType> list() {
        return leagueTypeRepository.findAllByOrderByNameAsc();
    }

    public LeagueType get(Long id) {
        return leagueTypeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Lig türü bulunamadı (id: " + id + ")"));
    }

    /**
     * Case-insensitive uniqueness check on top of the DB's case-sensitive UNIQUE
     * constraint — the whole point of this table is to stop "Premier League" and
     * "premier league" (or a typo like "Premierlig") from silently becoming two
     * different categories.
     */
    public LeagueType create(String name, User admin) {
        String trimmed = name.trim();
        if (leagueTypeRepository.findByNameIgnoreCase(trimmed).isPresent()) {
            throw ApiException.badRequest("\"" + trimmed + "\" adında bir lig türü zaten var");
        }
        LeagueType leagueType = leagueTypeRepository.save(LeagueType.builder().name(trimmed).build());
        log.info("Admin {} created league type {} ({})", admin.getId(), leagueType.getId(), leagueType.getName());
        return leagueType;
    }
}
