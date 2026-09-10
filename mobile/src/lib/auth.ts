import AsyncStorage from "@react-native-async-storage/async-storage";
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

export const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

// AsyncStorage matches Zustand's StateStorage shape (getItem/setItem/removeItem). Only
// user+isGuest end up persisted here (see authStore.ts's partialize) — the refresh token
// itself lives in SecureStore instead, see lib/refreshToken.ts.
export const useAuthStore = createAuthStore(AsyncStorage);

const apiConfig = createApiConfiguration({
  basePath: API_BASE_URL,
  getAccessToken: () => useAuthStore.getState().accessToken,
  // No cookies on mobile — refresh/logout instead pass the SecureStore-held refresh
  // token explicitly in the request body (see AuthBootstrap.tsx / ProfileScreen.tsx).
});

export const authApi = new AuthApi(apiConfig);
export const teamsApi = new TeamsApi(apiConfig);
export const leagueTypesApi = new LeagueTypesApi(apiConfig);
export const leaguesApi = new LeaguesApi(apiConfig);
export const playersApi = new PlayersApi(apiConfig);
export const matchesApi = new MatchesApi(apiConfig);
export const standingsApi = new StandingsApi(apiConfig);
export const seasonsApi = new SeasonsApi(apiConfig);
