import { useEffect, useRef, useState, type ReactNode } from "react";
import { View, Text, ActivityIndicator } from "react-native";
import { authApi, useAuthStore } from "../lib/auth";
import { getRefreshToken, setRefreshToken, clearRefreshToken } from "../lib/refreshToken";
import { logInfo, logWarn, ResponseError } from "@fc27/shared";

/**
 * On first load, reads the SecureStore-held refresh token (if any — never set for a
 * guest or a fresh install) and calls POST /api/auth/refresh with it in the body (mobile
 * has no cookies, unlike web/src/components/AuthBootstrap.tsx). Success re-establishes
 * the session with a fresh access+refresh token pair (refresh tokens rotate on every use);
 * a 401 (missing/expired token) just falls back to guest.
 */
export function AuthBootstrap({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const setAccessToken = useAuthStore((state) => state.setAccessToken);
  const login = useAuthStore((state) => state.login);
  const logout = useAuthStore((state) => state.logout);
  const bootstrapStarted = useRef(false);

  useEffect(() => {
    if (bootstrapStarted.current) return;
    bootstrapStarted.current = true;

    (async () => {
      // AsyncStorage-backed persist() rehydrates asynchronously — without waiting for it,
      // `isGuest` still reads its pre-hydration default (true) on every cold start, which
      // would silently defeat "persistent auto-login" for a previously-logged-in user.
      if (!useAuthStore.persist.hasHydrated()) {
        await new Promise<void>((resolve) => {
          const unsub = useAuthStore.persist.onFinishHydration(() => {
            unsub();
            resolve();
          });
        });
      }
      if (useAuthStore.getState().isGuest) {
        setReady(true);
        return;
      }
      const refreshToken = await getRefreshToken();
      if (!refreshToken) {
        logInfo("bootstrap.noPersistedSession");
        logout();
        setReady(true);
        return;
      }
      try {
        const response = await authApi.refresh({ refreshRequest: { refreshToken } });
        if (response.accessToken && response.refreshToken && response.user) {
          await setRefreshToken(response.refreshToken);
          login(response.accessToken, {
            id: response.user.id!,
            displayName: response.user.displayName!,
            email: response.user.email,
            phone: response.user.phone,
            role: (response.user.role as "USER" | "ADMIN") ?? "USER",
          });
          logInfo("bootstrap.sessionRestored", { userId: response.user.id });
        } else {
          setAccessToken("");
        }
      } catch (err) {
        if (err instanceof ResponseError && err.response.status === 401) {
          logInfo("bootstrap.refreshTokenInvalid");
        } else {
          logWarn("bootstrap.refreshFailed", { error: String(err) });
        }
        await clearRefreshToken();
        logout();
      } finally {
        setReady(true);
      }
    })();
    // Runs once on mount — deliberately not re-run on login/logout state changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (!ready) {
    return (
      <View className="flex-1 items-center justify-center bg-bg dark:bg-bg-dark">
        <ActivityIndicator />
        <Text className="mt-2 text-sm text-text-faint dark:text-text-faint-dark">Yükleniyor…</Text>
      </View>
    );
  }

  return <>{children}</>;
}
