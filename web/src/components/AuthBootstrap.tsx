import { useEffect, useRef, useState, type ReactNode } from "react";
import { authApi, useAuthStore } from "../lib/auth";
import { logInfo, logWarn, ResponseError } from "@fc27/shared";

/**
 * On first load, silently tries POST /api/auth/refresh (web sends the httpOnly
 * refresh_token cookie automatically). Success re-establishes the session with a fresh
 * access token; a 401 (no cookie, or it expired) just falls back to guest/logged-out —
 * this is expected whenever "Remember me" wasn't used, not an error worth surfacing.
 */
export function AuthBootstrap({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const setAccessToken = useAuthStore((state) => state.setAccessToken);
  const login = useAuthStore((state) => state.login);
  const logout = useAuthStore((state) => state.logout);
  const isGuest = useAuthStore((state) => state.isGuest);
  // Guards against React StrictMode's dev-only double effect invocation: refresh tokens
  // rotate on every use (old one revoked server-side), so firing this call twice can race
  // and spuriously log the user out when the second call arrives with an already-rotated
  // token. Without this guard that's a real, if rare, startup bug — not just test noise.
  const bootstrapStarted = useRef(false);

  useEffect(() => {
    if (isGuest) {
      setReady(true);
      return;
    }
    if (bootstrapStarted.current) {
      return;
    }
    bootstrapStarted.current = true;
    authApi
      .refresh()
      .then((response) => {
        if (response.accessToken && response.user) {
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
      })
      .catch((err) => {
        if (err instanceof ResponseError && err.response.status === 401) {
          logInfo("bootstrap.noPersistedSession");
        } else {
          logWarn("bootstrap.refreshFailed", { error: String(err) });
        }
        logout();
      })
      .finally(() => setReady(true));
    // Runs once on mount — deliberately not re-run on login/logout state changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (!ready) {
    return (
      <div className="flex min-h-screen items-center justify-center text-sm text-text-faint">
        Yükleniyor…
      </div>
    );
  }

  return <>{children}</>;
}
