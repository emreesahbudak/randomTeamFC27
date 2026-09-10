package randomTeamFC27.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import randomTeamFC27.entity.LeagueType;
import randomTeamFC27.entity.Role;
import randomTeamFC27.entity.Team;
import randomTeamFC27.entity.User;
import randomTeamFC27.repository.LeagueTypeRepository;
import randomTeamFC27.repository.TeamRepository;
import randomTeamFC27.repository.UserRepository;
import randomTeamFC27.security.JwtService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TeamControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private LeagueTypeRepository leagueTypeRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtService jwtService;

    private String adminToken() {
        User admin = userRepository.save(User.builder()
                .displayName("Admin").email("admin+" + System.nanoTime() + "@example.com").role(Role.ADMIN).build());
        return jwtService.generateAccessToken(admin);
    }

    private String userToken() {
        User user = userRepository.save(User.builder()
                .displayName("Regular").email("user+" + System.nanoTime() + "@example.com").role(Role.USER).build());
        return jwtService.generateAccessToken(user);
    }

    private LeagueType leagueType(String name) {
        return leagueTypeRepository.save(LeagueType.builder().name(name + " " + System.nanoTime()).build());
    }

    private Team seedTeam(String code, int stars, LeagueType leagueType) {
        return teamRepository.save(Team.builder()
                .name(code + " FC").code(code).starLevel(stars).leagueType(leagueType).active(true).build());
    }

    @Test
    void listIsPublicAndFiltersByStarsAndLeague() throws Exception {
        LeagueType ember = leagueType("Ember Circuit");
        LeagueType vanta = leagueType("Vanta League");
        seedTeam("AAA", 5, ember);
        seedTeam("BBB", 2, ember);
        seedTeam("CCC", 5, vanta);

        mockMvc.perform(get("/api/teams").param("stars", "5").param("leagueTypeId", String.valueOf(ember.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='AAA')]").exists())
                .andExpect(jsonPath("$[?(@.code=='BBB')]").doesNotExist())
                .andExpect(jsonPath("$[?(@.code=='CCC')]").doesNotExist());
    }

    @Test
    void createRequiresAdminRole() throws Exception {
        LeagueType ember = leagueType("Ember Circuit");
        String body = objectMapper.writeValueAsString(new TeamBody("Solar FC", "SOL", null, null, 5, ember.getId()));

        mockMvc.perform(post("/api/teams").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SOL"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.leagueTypeId").value(ember.getId()));
    }

    @Test
    void createRejectsUnknownLeagueType() throws Exception {
        String body = objectMapper.writeValueAsString(new TeamBody("Solar FC", "SOL", null, null, 5, 9_999_999L));

        mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json").content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanFullyManageATeamLifecycle() throws Exception {
        String token = adminToken();
        LeagueType vanta = leagueType("Vanta League");
        String createBody = objectMapper.writeValueAsString(new TeamBody("Vanta Wolves", "VAN", null, null, 5, vanta.getId()));

        String createResponse = mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(createResponse).get("id").asLong();

        // PATCH only star level
        mockMvc.perform(patch("/api/teams/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"starLevel\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.starLevel").value(3))
                .andExpect(jsonPath("$.name").value("Vanta Wolves"));

        // PUT replaces everything
        String replaceBody = objectMapper.writeValueAsString(new TeamBody("Vanta Wolves United", "VAN", null, null, 4, vanta.getId()));
        mockMvc.perform(put("/api/teams/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(replaceBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Vanta Wolves United"))
                .andExpect(jsonPath("$.starLevel").value(4));

        // DELETE soft-deletes — team drops out of the public list but the row survives
        mockMvc.perform(delete("/api/teams/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + id + ")]").doesNotExist());

        mockMvc.perform(get("/api/teams/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // includeInactive=true is how the admin management view sees it's really gone —
        // without this, a deactivated team disappears from admin's own list too.
        mockMvc.perform(get("/api/teams").param("includeInactive", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + id + " && @.active==false)]").exists());
    }

    @Test
    void createRejectsInvalidStarLevel() throws Exception {
        LeagueType ember = leagueType("Ember Circuit");
        String body = objectMapper.writeValueAsString(new TeamBody("Bad Team", "BAD", null, null, 9, ember.getId()));

        mockMvc.perform(post("/api/teams")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUnknownTeamReturns404() throws Exception {
        mockMvc.perform(get("/api/teams/999999"))
                .andExpect(status().isNotFound());
    }

    private record TeamBody(String name, String code, String crestUrl, String colorHex, int starLevel, Long leagueTypeId) {
    }
}
