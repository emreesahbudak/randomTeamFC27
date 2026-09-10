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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LeagueControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private LeagueTypeRepository leagueTypeRepository;
    @Autowired
    private JwtService jwtService;

    private String tokenFor(User user) {
        return jwtService.generateAccessToken(user);
    }

    private User newUser(String name) {
        return userRepository.save(User.builder()
                .displayName(name).email(name.toLowerCase() + "+" + System.nanoTime() + "@example.com").role(Role.USER).build());
    }

    private Team seedTeam(String code) {
        LeagueType lt = leagueTypeRepository.save(LeagueType.builder().name("League " + System.nanoTime()).build());
        return teamRepository.save(Team.builder().name(code + " FC").code(code).starLevel(4).leagueType(lt).active(true).build());
    }

    @Test
    void creatingALeagueRequiresAuth() throws Exception {
        mockMvc.perform(post("/api/leagues").contentType("application/json").content("{\"name\":\"Friday Nights\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fullLeagueLifecycle_createPlayersMatchesStandingsStatsReset() throws Exception {
        User owner = newUser("Owner");
        String ownerToken = tokenFor(owner);
        Team teamA = seedTeam("AAA");
        Team teamB = seedTeam("BBB");

        // create league
        String leagueJson = mockMvc.perform(post("/api/leagues")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json").content("{\"name\":\"Friday Nights\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long leagueId = objectMapper.readTree(leagueJson).get("id").asLong();

        // add two players
        long ahmetId = addPlayer(leagueId, ownerToken, "Ahmet");
        long mehmetId = addPlayer(leagueId, ownerToken, "Mehmet");

        // duplicate name rejected
        mockMvc.perform(post("/api/leagues/" + leagueId + "/players")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType("application/json").content("{\"displayName\":\"Ahmet\"}"))
                .andExpect(status().isBadRequest());

        // record two matches: Ahmet beats Mehmet 3-1, then draw 1-1
        recordMatch(leagueId, ownerToken, ahmetId, mehmetId, teamA.getId(), teamB.getId(), 3, 1);
        recordMatch(leagueId, ownerToken, ahmetId, mehmetId, teamA.getId(), teamB.getId(), 1, 1);

        // standings reflect it
        mockMvc.perform(get("/api/leagues/" + leagueId + "/standings").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("Ahmet"))
                .andExpect(jsonPath("$[0].points").value(4)) // 1 win + 1 draw
                .andExpect(jsonPath("$[0].played").value(2))
                .andExpect(jsonPath("$[1].displayName").value("Mehmet"))
                .andExpect(jsonPath("$[1].points").value(1));

        // stats: Ahmet has more goals (4) than Mehmet (2) -> top scorer
        mockMvc.perform(get("/api/leagues/" + leagueId + "/stats").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topScorer.displayName").value("Ahmet"))
                .andExpect(jsonPath("$.headToHead[0].player1Wins").exists());

        // match history
        mockMvc.perform(get("/api/leagues/" + leagueId + "/matches").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].team1Name").value("AAA FC"));

        // a different user cannot see/manage this league at all
        String intruderToken = tokenFor(newUser("Intruder"));
        mockMvc.perform(get("/api/leagues/" + leagueId + "/standings").header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/leagues/" + leagueId + "/reset").header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isForbidden());

        // remove a player (soft) — drops out of the default roster listing
        mockMvc.perform(delete("/api/leagues/" + leagueId + "/players/" + mehmetId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/leagues/" + leagueId + "/players").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.displayName=='Mehmet')]").doesNotExist());
        mockMvc.perform(get("/api/leagues/" + leagueId + "/players").param("includeInactive", "true")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.displayName=='Mehmet' && @.active==false)]").exists());

        // reset closes the current season and opens a new one — current view goes to zero...
        mockMvc.perform(post("/api/leagues/" + leagueId + "/reset").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/leagues/" + leagueId + "/matches").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/leagues/" + leagueId + "/standings").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].played").value(0));

        // ...but nothing was deleted: the closed season is browsable under "Geçmiş Ligler"
        // with its original scores/standings/matches intact.
        String seasonsJson = mockMvc.perform(get("/api/leagues/" + leagueId + "/seasons").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Sezon 1"))
                .andExpect(jsonPath("$[0].endedAt").exists())
                .andReturn().getResponse().getContentAsString();
        long pastSeasonId = objectMapper.readTree(seasonsJson).get(0).get("id").asLong();

        mockMvc.perform(get("/api/leagues/" + leagueId + "/seasons/" + pastSeasonId + "/standings")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("Ahmet"))
                .andExpect(jsonPath("$[0].points").value(4));
        mockMvc.perform(get("/api/leagues/" + leagueId + "/seasons/" + pastSeasonId + "/matches")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // a non-owner can't see past seasons either; the current (open) season isn't "past"
        mockMvc.perform(get("/api/leagues/" + leagueId + "/seasons").header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isForbidden());

        // listing my leagues includes it
        mockMvc.perform(get("/api/leagues").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + leagueId + ")]").exists());
    }

    @Test
    void recordingAMatchRejectsAPlayerFromAnotherLeague() throws Exception {
        User owner = newUser("Owner2");
        String token = tokenFor(owner);
        Team teamA = seedTeam("CCC");
        Team teamB = seedTeam("DDD");

        long league1 = createLeague(token, "League One");
        long league2 = createLeague(token, "League Two");
        long playerInLeague2 = addPlayer(league2, token, "Outsider");
        long playerInLeague1 = addPlayer(league1, token, "Insider");

        String body = objectMapper.writeValueAsString(new MatchBody(playerInLeague1, playerInLeague2, teamA.getId(), teamB.getId(), 1, 0));
        mockMvc.perform(post("/api/leagues/" + league1 + "/matches")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(body))
                .andExpect(status().isNotFound());
    }

    private long createLeague(String token, String name) throws Exception {
        String json = mockMvc.perform(post("/api/leagues")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private long addPlayer(long leagueId, String token, String displayName) throws Exception {
        String json = mockMvc.perform(post("/api/leagues/" + leagueId + "/players")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"displayName\":\"" + displayName + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private void recordMatch(long leagueId, String token, long p1, long p2, long t1, long t2, int s1, int s2) throws Exception {
        String body = objectMapper.writeValueAsString(new MatchBody(p1, p2, t1, t2, s1, s2));
        mockMvc.perform(post("/api/leagues/" + leagueId + "/matches")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated());
    }

    private record MatchBody(long player1Id, long player2Id, long team1Id, long team2Id, int score1, int score2) {
    }
}
