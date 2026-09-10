import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Header } from "./Header";
import { useAuthStore, authApi } from "../lib/auth";

vi.mock("../lib/auth", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../lib/auth")>();
  return {
    ...actual,
    authApi: { ...actual.authApi, logout: vi.fn().mockResolvedValue(undefined) },
  };
});

function renderHeader() {
  return render(
    <MemoryRouter>
      <Header />
    </MemoryRouter>,
  );
}

describe("Header", () => {
  beforeEach(() => {
    useAuthStore.getState().logout();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("shows a guest avatar and a giriş yap link when logged out", async () => {
    renderHeader();
    const avatarButton = screen.getByRole("button", { name: /giriş yap/i });
    await userEvent.click(avatarButton);
    expect(screen.getByRole("link", { name: /giriş yap/i })).toBeInTheDocument();
  });

  it("shows the user's name and a logout option when logged in", async () => {
    useAuthStore.getState().login("token-123", {
      id: 1,
      displayName: "Emre",
      email: "emre@example.com",
      role: "USER",
    });
    renderHeader();

    const avatarButton = screen.getByRole("button", { name: /hesap menüsü/i });
    await userEvent.click(avatarButton);

    expect(screen.getByText("Emre")).toBeInTheDocument();
    expect(screen.getByText("emre@example.com")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /çıkış yap/i })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /yönetici: takımlar/i })).not.toBeInTheDocument();
  });

  it("shows an Admin link only for ADMIN users", async () => {
    useAuthStore.getState().login("token-123", {
      id: 1,
      displayName: "Admin",
      email: "admin@fc27.app",
      role: "ADMIN",
    });
    renderHeader();

    await userEvent.click(screen.getByRole("button", { name: /hesap menüsü/i }));

    expect(screen.getByRole("link", { name: /yönetici: takımlar/i })).toBeInTheDocument();
  });

  it("logout clears the session and calls the backend", async () => {
    useAuthStore.getState().login("token-123", { id: 1, displayName: "Emre", role: "USER" });
    renderHeader();

    await userEvent.click(screen.getByRole("button", { name: /hesap menüsü/i }));
    await userEvent.click(screen.getByRole("button", { name: /çıkış yap/i }));

    expect(authApi.logout).toHaveBeenCalled();
    expect(useAuthStore.getState().isGuest).toBe(true);
    expect(useAuthStore.getState().user).toBeNull();
  });
});
