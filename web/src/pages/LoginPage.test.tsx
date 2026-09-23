import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { LoginPage } from "./LoginPage";
import { authApi, leaguesApi, useAuthStore } from "../lib/auth";

const navigateMock = vi.fn();
vi.mock("react-router-dom", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-router-dom")>();
  return { ...actual, useNavigate: () => navigateMock };
});

vi.mock("../lib/auth", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../lib/auth")>();
  return {
    ...actual,
    authApi: {
      ...actual.authApi,
      sendEmailOtp: vi.fn(),
      verifyEmailOtp: vi.fn(),
    },
    leaguesApi: {
      ...actual.leaguesApi,
      listMyLeagues: vi.fn(),
    },
  };
});

function renderLogin() {
  return render(
    <MemoryRouter>
      <LoginPage />
    </MemoryRouter>,
  );
}

describe("LoginPage", () => {
  beforeEach(() => {
    useAuthStore.getState().logout();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("continuing as guest navigates home without calling the backend", async () => {
    renderLogin();
    await userEvent.click(screen.getByRole("button", { name: /misafir/i }));
    await userEvent.click(screen.getByRole("button", { name: /misafir olarak devam et/i }));

    expect(navigateMock).toHaveBeenCalledWith("/");
    expect(authApi.sendEmailOtp).not.toHaveBeenCalled();
  });

  it("sends an email code then verifies it and logs in, landing on home when a league exists", async () => {
    vi.mocked(authApi.sendEmailOtp).mockResolvedValue(undefined);
    vi.mocked(authApi.verifyEmailOtp).mockResolvedValue({
      accessToken: "access-123",
      refreshToken: "refresh-123",
      user: { id: 7, displayName: "Emre", email: "emre@example.com", role: "USER" },
    });
    vi.mocked(leaguesApi.listMyLeagues).mockResolvedValue([{ id: 1, name: "Cuma Gecesi" }]);

    renderLogin();

    await userEvent.type(screen.getByLabelText(/e-posta adresi/i), "emre@example.com");
    await userEvent.click(screen.getByRole("button", { name: /6 haneli kod gönder/i }));

    expect(authApi.sendEmailOtp).toHaveBeenCalledWith({
      emailOtpSendRequest: { email: "emre@example.com" },
    });

    await screen.findByText(/kod gönderildi/i);
    await userEvent.type(screen.getByLabelText(/kodu gir/i), "123456");
    await userEvent.click(screen.getByRole("button", { name: /doğrula ve giriş yap/i }));

    expect(authApi.verifyEmailOtp).toHaveBeenCalledWith({
      emailOtpVerifyRequest: { email: "emre@example.com", code: "123456", rememberMe: true },
    });
    expect(useAuthStore.getState().user?.displayName).toBe("Emre");
    expect(useAuthStore.getState().isGuest).toBe(false);
    await vi.waitFor(() => expect(navigateMock).toHaveBeenCalledWith("/"));
  });

  it("sends a league-less user to their profile to create one instead of the wheel", async () => {
    vi.mocked(authApi.sendEmailOtp).mockResolvedValue(undefined);
    vi.mocked(authApi.verifyEmailOtp).mockResolvedValue({
      accessToken: "access-123",
      refreshToken: "refresh-123",
      user: { id: 7, displayName: "Emre", email: "emre@example.com", role: "USER" },
    });
    vi.mocked(leaguesApi.listMyLeagues).mockResolvedValue([]);

    renderLogin();

    await userEvent.type(screen.getByLabelText(/e-posta adresi/i), "emre@example.com");
    await userEvent.click(screen.getByRole("button", { name: /6 haneli kod gönder/i }));
    await screen.findByText(/kod gönderildi/i);
    await userEvent.type(screen.getByLabelText(/kodu gir/i), "123456");
    await userEvent.click(screen.getByRole("button", { name: /doğrula ve giriş yap/i }));

    await vi.waitFor(() => expect(navigateMock).toHaveBeenCalledWith("/profile"));
  });

  it("shows an error message when sending the code fails", async () => {
    vi.mocked(authApi.sendEmailOtp).mockRejectedValue(new Error("network down"));

    renderLogin();
    await userEvent.type(screen.getByLabelText(/e-posta adresi/i), "emre@example.com");
    await userEvent.click(screen.getByRole("button", { name: /6 haneli kod gönder/i }));

    expect(await screen.findByText(/bir şeyler ters gitti/i)).toBeInTheDocument();
    expect(useAuthStore.getState().isGuest).toBe(true);
  });
});
