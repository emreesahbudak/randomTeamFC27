import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";
import { logInfo } from "./logger";
/**
 * Factory, not a singleton — web and mobile each pass their own storage adapter (Web:
 * localStorage; Mobile: AsyncStorage — see Steps 7/8) via Zustand's `StateStorage`
 * interface, keeping this file free of any platform-specific dependency. The refresh
 * token itself is deliberately NOT part of this store: on web it lives in an HTTP-only
 * cookie the backend manages (invisible to JS by design); on mobile the platform layer
 * reads/writes it directly via SecureStore, since general-purpose persisted storage
 * (AsyncStorage) is not encrypted and the wrong place for a long-lived credential.
 */
export function createAuthStore(storage) {
    return create()(persist((set) => ({
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
    }), {
        name: "fc27-auth",
        storage: createJSONStorage(() => storage),
        // accessToken is short-lived (15 min) and re-obtained via a silent refresh on
        // startup — persisting it would just be a stale, soon-invalid credential at rest.
        partialize: (state) => ({ user: state.user, isGuest: state.isGuest }),
    }));
}
