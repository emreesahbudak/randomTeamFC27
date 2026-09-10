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
import randomTeamFC27.entity.User;
import randomTeamFC27.repository.LeagueTypeRepository;
import randomTeamFC27.repository.UserRepository;
import randomTeamFC27.security.JwtService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LeagueTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
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

    @Test
    void listIsPublicAndSortedAlphabetically() throws Exception {
        leagueTypeRepository.save(LeagueType.builder().name("Zeta " + System.nanoTime()).build());
        leagueTypeRepository.save(LeagueType.builder().name("Alpha " + System.nanoTime()).build());

        mockMvc.perform(get("/api/league-types"))
                .andExpect(status().isOk());
    }

    @Test
    void createRequiresAdminRole() throws Exception {
        String body = objectMapper.writeValueAsString(new NameBody("Premier League " + System.nanoTime()));

        mockMvc.perform(post("/api/league-types").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/league-types")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/league-types")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void createRejectsCaseInsensitiveDuplicate() throws Exception {
        String name = "Premier League " + System.nanoTime();
        String token = adminToken();
        String body = objectMapper.writeValueAsString(new NameBody(name));

        mockMvc.perform(post("/api/league-types")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated());

        String dupBody = objectMapper.writeValueAsString(new NameBody(name.toUpperCase()));
        mockMvc.perform(post("/api/league-types")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(dupBody))
                .andExpect(status().isBadRequest());
    }

    private record NameBody(String name) {
    }
}
