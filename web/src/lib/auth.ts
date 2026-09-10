import {
  createAuthStore,
  createApiConfiguration,
  AuthApi,
  TeamsApi,
  LeagueTypesApi,
  LeaguesApi,
  PlayersApi,
  MatchesApi,
  StandingsApi,
  SeasonsApi,
} from "@fc27/shared";

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

// localStorage already matches Zustand's StateStorage shape (getItem/setItem/removeItem
// with string values) — no adapter wrapper needed, unlike mobile's SecureStore/AsyncStorage.
export const useAuthStore = createAuthStore(localStorage);

const apiConfig = createApiConfiguration({
  basePath: API_BASE_URL,
  getAccessToken: () => useAuthStore.getState().accessToken,
  // Sends the httpOnly refresh_token cookie on /api/auth/refresh and /api/auth/logout.
  credentials: "include",
});

export const authApi = new AuthApi(apiConfig);
export const teamsApi = new TeamsApi(apiConfig);
export const leagueTypesApi = new LeagueTypesApi(apiConfig);
export const leaguesApi = new LeaguesApi(apiConfig);
export const playersApi = new PlayersApi(apiConfig);
export const matchesApi = new MatchesApi(apiConfig);
export const standingsApi = new StandingsApi(apiConfig);
export const seasonsApi = new SeasonsApi(apiConfig);
