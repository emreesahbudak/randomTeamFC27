import { create } from "zustand";
import { persist, createJSONStorage, type StateStorage } from "zustand/middleware";
import { logInfo } from "./logger";

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
export function createAuthStore(storage: StateStorage) {
  return create<AuthState>()(
    persist(
      (set) => ({
        accessToken: null,
        user: null,
        isGuest: true,
        login: (accessToken, user) => {
          logInfo("auth.login", { userId: user.id, role: user.role });
          set({ accessToken, user, isGuest: false });
        },
        setAccessToken: (accessToken) => set({ accessToken }),
        logout: () => {
          logInfo("auth.logout");
          set({ accessToken: null, user: null, isGuest: true });
        },
      }),
      {
        name: "fc27-auth",
        storage: createJSONStorage(() => storage),
        // accessToken is short-lived (15 min) and re-obtained via a silent refresh on
        // startup — persisting it would just be a stale, soon-invalid credential at rest.
        partialize: (state) => ({ user: state.user, isGuest: state.isGuest }),
      },
    ),
  );
}

/** Inferred (not hand-declared) so it keeps the `persist` mutator's API — e.g.
 *  `.persist.hasHydrated()` / `.persist.onFinishHydration()`, which mobile's
 *  AuthBootstrap needs to await AsyncStorage rehydration before reading `isGuest`. */
export type AuthStore = ReturnType<typeof createAuthStore>;
