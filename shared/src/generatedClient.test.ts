import { AuthApi, LeagueTypesApi, TeamsApi, LeaguesApi, PlayersApi, MatchesApi, StandingsApi, SeasonsApi, Configuration } from "./generated";

/**
 * Validates that `npm run generate:api` (run automatically via `pretest`, from the
 * checked-in openapi-spec.json — see scripts/export-openapi-spec.mjs to refresh it after
 * a backend change) produces a usable client: the right API classes exist, are
 * instantiable, and expose the operations the backend controllers define.
 */
describe("generated OpenAPI client", () => {
  const config = new Configuration({ basePath: "http://localhost:8080" });

  it("exposes one API class per controller tag", () => {
    expect(new AuthApi(config)).toBeInstanceOf(AuthApi);
    expect(new LeagueTypesApi(config)).toBeInstanceOf(LeagueTypesApi);
    expect(new TeamsApi(config)).toBeInstanceOf(TeamsApi);
    expect(new LeaguesApi(config)).toBeInstanceOf(LeaguesApi);
    expect(new PlayersApi(config)).toBeInstanceOf(PlayersApi);
    expect(new MatchesApi(config)).toBeInstanceOf(MatchesApi);
    expect(new StandingsApi(config)).toBeInstanceOf(StandingsApi);
    expect(new SeasonsApi(config)).toBeInstanceOf(SeasonsApi);
  });

  it("AuthApi exposes every auth endpoint", () => {
    const auth = new AuthApi(config);
    const methodNames = [
      "loginWithGoogle",
      "sendEmailOtp",
      "verifyEmailOtp",
      "sendSmsOtp",
      "verifySmsOtp",
      "refresh",
      "logout",
    ];
    for (const name of methodNames) {
      expect(typeof (auth as unknown as Record<string, unknown>)[name]).toBe("function");
    }
  });

  it("TeamsApi exposes full CRUD with unique, collision-free operation names", () => {
    const teams = new TeamsApi(config);
    const methodNames = ["listTeams", "getTeam", "createTeam", "replaceTeam", "patchTeam", "deactivateTeam"];
    for (const name of methodNames) {
      expect(typeof (teams as unknown as Record<string, unknown>)[name]).toBe("function");
    }
  });

  it("LeagueTypesApi exposes list + create without the create1/list1 operationId collision", () => {
    const leagueTypes = new LeagueTypesApi(config);
    expect(typeof leagueTypes.listLeagueTypes).toBe("function");
    expect(typeof leagueTypes.createLeagueType).toBe("function");
  });

  it("LeaguesApi exposes create/list/reset", () => {
    const leagues = new LeaguesApi(config);
    for (const name of ["createLeague", "listMyLeagues", "resetLeague"]) {
      expect(typeof (leagues as unknown as Record<string, unknown>)[name]).toBe("function");
    }
  });

  it("PlayersApi exposes add/list/remove", () => {
    const players = new PlayersApi(config);
    for (const name of ["addPlayer", "listPlayers", "removePlayer"]) {
      expect(typeof (players as unknown as Record<string, unknown>)[name]).toBe("function");
    }
  });

  it("MatchesApi exposes record/list", () => {
    const matches = new MatchesApi(config);
    expect(typeof matches.recordMatch).toBe("function");
    expect(typeof matches.listMatches).toBe("function");
  });

  it("StandingsApi exposes standings + stats", () => {
    const standings = new StandingsApi(config);
    expect(typeof standings.getStandings).toBe("function");
    expect(typeof standings.getLeagueStats).toBe("function");
  });

  it("SeasonsApi exposes past-season listing + standings/stats/matches", () => {
    const seasons = new SeasonsApi(config);
    for (const name of ["listPastSeasons", "getPastSeasonStandings", "getPastSeasonStats", "getPastSeasonMatches"]) {
      expect(typeof (seasons as unknown as Record<string, unknown>)[name]).toBe("function");
    }
  });
});
