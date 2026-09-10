import { Configuration } from "./generated";

export interface CreateApiConfigOptions {
  basePath: string;
  /** Read the current access token from wherever the platform's auth store lives.
   *  Returning null/undefined (guest) simply omits the Authorization header. */
  getAccessToken: () => string | null | undefined;
  /** Web needs `'include'` so the refresh-token cookie is sent; mobile has no cookies
   *  at all and should leave this unset. */
  credentials?: RequestCredentials;
}

/**
 * Builds a `Configuration` for the generated `AuthApi`/`TeamsApi`/`LeagueTypesApi` clients
 * that attaches the current access token to every request automatically — callers never
 * touch headers directly. `getAccessToken` is read lazily on every call (not captured
 * once), so it stays correct across login/logout/refresh without needing to rebuild the
 * Configuration.
 */
export function createApiConfiguration(options: CreateApiConfigOptions): Configuration {
  return new Configuration({
    basePath: options.basePath,
    credentials: options.credentials,
    accessToken: async () => options.getAccessToken() ?? "",
  });
}
