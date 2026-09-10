import type { StateStorage } from "zustand/middleware";
import { createAuthStore, type AuthUser } from "./authStore";

function createMemoryStorage(): StateStorage {
  const map = new Map<string, string>();
  return {
    getItem: (name) => map.get(name) ?? null,
    setItem: (name, value) => {
      map.set(name, value);
    },
    removeItem: (name) => {
      map.delete(name);
    },
  };
}

const user: AuthUser = { id: 1, displayName: "Emre", email: "e@example.com", role: "USER" };

describe("authStore", () => {
  it("starts as a guest with no user or token", () => {
    const store = createAuthStore(createMemoryStorage());

    const state = store.getState();
    expect(state.isGuest).toBe(true);
    expect(state.user).toBeNull();
    expect(state.accessToken).toBeNull();
  });

  it("login sets accessToken, user, and clears guest status", () => {
    const store = createAuthStore(createMemoryStorage());

    store.getState().login("access-token-123", user);

    const state = store.getState();
    expect(state.accessToken).toBe("access-token-123");
    expect(state.user).toEqual(user);
    expect(state.isGuest).toBe(false);
  });

  it("setAccessToken updates only the token (e.g. after a silent refresh)", () => {
    const store = createAuthStore(createMemoryStorage());
    store.getState().login("old-token", user);

    store.getState().setAccessToken("new-token");

    const state = store.getState();
    expect(state.accessToken).toBe("new-token");
    expect(state.user).toEqual(user);
  });

  it("logout clears everything and returns to guest", () => {
    const store = createAuthStore(createMemoryStorage());
    store.getState().login("access-token-123", user);

    store.getState().logout();

    const state = store.getState();
    expect(state.accessToken).toBeNull();
    expect(state.user).toBeNull();
    expect(state.isGuest).toBe(true);
  });

  it("persists user/isGuest but never the access token", async () => {
    const storage = createMemoryStorage();
    const store = createAuthStore(storage);
    store.getState().login("access-token-123", user);

    // Persist middleware writes asynchronously — flush microtasks.
    await new Promise((resolve) => setTimeout(resolve, 0));

    const raw = await storage.getItem("fc27-auth");
    expect(raw).not.toBeNull();
    const parsed = JSON.parse(raw!);
    expect(parsed.state.user).toEqual(user);
    expect(parsed.state.isGuest).toBe(false);
    expect(parsed.state.accessToken).toBeUndefined();
  });

  it("rehydrates user/isGuest from storage into a fresh store instance", async () => {
    const storage = createMemoryStorage();
    const first = createAuthStore(storage);
    first.getState().login("access-token-123", user);
    await new Promise((resolve) => setTimeout(resolve, 0));

    const second = createAuthStore(storage);
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(second.getState().user).toEqual(user);
    expect(second.getState().isGuest).toBe(false);
    // The access token is never persisted, so a fresh instance starts without one —
    // the platform layer is expected to silently refresh on startup.
    expect(second.getState().accessToken).toBeNull();
  });
});
