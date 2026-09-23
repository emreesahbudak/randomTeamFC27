import { type StateStorage } from "zustand/middleware";
export interface AuthUser {
    id: number;
    displayName: string;
    email?: string | null;
    phone?: string | null;
    role: "USER" | "ADMIN";
}
export interface AuthState {
    /** null = guest. Never persisted — see the `partialize` note below. */
    accessToken: string | null;
    /** Persisted so the UI can show "welcome back, X" immediately, before the silent
     *  refresh (done by the platform layer on startup) resolves a fresh access token. */
    user: AuthUser | null;
    isGuest: boolean;
    login: (accessToken: string, user: AuthUser) => void;
    /** Called after a successful POST /api/auth/refresh with just the new access token. */
    setAccessToken: (accessToken: string) => void;
    logout: () => void;
}
/**
 * Factory, not a singleton — web and mobile each pass their own storage adapter (Web:
 * localStorage; Mobile: AsyncStorage — see Steps 7/8) via Zustand's `StateStorage`
 * interface, keeping this file free of any platform-specific dependency. The refresh
 * token itself is deliberately NOT part of this store: on web it lives in an HTTP-only
 * cookie the backend manages (invisible to JS by design); on mobile the platform layer
 * reads/writes it directly via SecureStore, since general-purpose persisted storage
 * (AsyncStorage) is not encrypted and the wrong place for a long-lived credential.
 */
export declare function createAuthStore(storage: StateStorage): import("zustand").UseBoundStore<Omit<import("zustand").StoreApi<AuthState>, "setState" | "persist"> & {
    setState(partial: AuthState | Partial<AuthState> | ((state: AuthState) => AuthState | Partial<AuthState>), replace?: false | undefined): unknown;
    setState(state: AuthState | ((state: AuthState) => AuthState), replace: true): unknown;
    persist: {
        setOptions: (options: Partial<import("zustand/middleware").PersistOptions<AuthState, unknown, unknown>>) => void;
        clearStorage: () => void;
        rehydrate: () => Promise<void> | void;
        hasHydrated: () => boolean;
        onHydrate: (fn: (state: AuthState) => void) => () => void;
        onFinishHydration: (fn: (state: AuthState) => void) => () => void;
        getOptions: () => Partial<import("zustand/middleware").PersistOptions<AuthState, unknown, unknown>>;
    };
}>;
/** Inferred (not hand-declared) so it keeps the `persist` mutator's API — e.g.
 *  `.persist.hasHydrated()` / `.persist.onFinishHydration()`, which mobile's
 *  AuthBootstrap needs to await AsyncStorage rehydration before reading `isGuest`. */
export type AuthStore = ReturnType<typeof createAuthStore>;
