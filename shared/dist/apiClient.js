import { Configuration } from "./generated";
/**
 * Builds a `Configuration` for the generated `AuthApi`/`TeamsApi`/`LeagueTypesApi` clients
 * that attaches the current access token to every request automatically — callers never
 * touch headers directly. `getAccessToken` is read lazily on every call (not captured
 * once), so it stays correct across login/logout/refresh without needing to rebuild the
 * Configuration.
 */
export function createApiConfiguration(options) {
    return new Configuration({
        basePath: options.basePath,
        credentials: options.credentials,
        accessToken: async () => options.getAccessToken() ?? "",
    });
}
