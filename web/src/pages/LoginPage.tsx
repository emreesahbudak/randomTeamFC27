import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { authApi, useAuthStore } from "../lib/auth";
import { logInfo, logWarn, ResponseError } from "@fc27/shared";

type Tab = "guest" | "email";
type EmailStep = "enter-email" | "enter-code";

export function LoginPage() {
  const navigate = useNavigate();
  const login = useAuthStore((state) => state.login);
  const [tab, setTab] = useState<Tab>("email");

  const [step, setStep] = useState<EmailStep>("enter-email");
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [rememberMe, setRememberMe] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function extractErrorMessage(err: unknown): Promise<string> {
    if (err instanceof ResponseError) {
      try {
        const body = await err.response.json();
        if (typeof body?.message === "string") return body.message;
      } catch {
        // fall through to generic message below
      }
    }
    return "Bir şeyler ters gitti. Lütfen tekrar dene.";
  }

  async function handleSendCode(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await authApi.sendEmailOtp({ emailOtpSendRequest: { email } });
      logInfo("login.otpSent", { email });
      setStep("enter-code");
    } catch (err) {
      logWarn("login.otpSendFailed", { error: String(err) });
      setError(await extractErrorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  async function handleVerifyCode(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const response = await authApi.verifyEmailOtp({
        emailOtpVerifyRequest: { email, code, rememberMe },
      });
      if (!response.accessToken || !response.user) {
        throw new Error("Malformed login response");
      }
      login(response.accessToken, {
        id: response.user.id!,
        displayName: response.user.displayName!,
        email: response.user.email,
        phone: response.user.phone,
        role: (response.user.role as "USER" | "ADMIN") ?? "USER",
      });
      navigate("/");
    } catch (err) {
      logWarn("login.otpVerifyFailed", { error: String(err) });
      setError(await extractErrorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  function continueAsGuest() {
    logInfo("login.continueAsGuest");
    navigate("/");
  }

  return (
    <div className="mx-auto flex max-w-md flex-col gap-6 px-4 py-10">
      <div className="rounded-2xl border border-border-soft bg-surface p-7">
        <div className="mb-6 text-center">
          <div
            className="mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-accent to-accent-dim font-display text-lg font-bold text-accent-ink"
            style={{ clipPath: "polygon(50% 0%,100% 18%,100% 62%,50% 100%,0% 62%,0% 18%)" }}
          >
            27
          </div>
          <h1 className="text-xl font-bold text-text">Tekrar hoş geldin</h1>
          <p className="mt-1 text-sm text-text-faint">Takım çekmek ve ligini takip etmek için giriş yap</p>
        </div>

        <div className="mb-5 grid grid-cols-2 gap-1.5 rounded-xl bg-surface-2 p-1">
          <button
            type="button"
            onClick={() => setTab("email")}
            className={`rounded-lg py-2.5 text-sm font-bold ${
              tab === "email" ? "bg-surface text-text shadow-sm" : "text-text-dim"
            }`}
          >
            E-posta
          </button>
          <button
            type="button"
            onClick={() => setTab("guest")}
            className={`rounded-lg py-2.5 text-sm font-bold ${
              tab === "guest" ? "bg-surface text-text shadow-sm" : "text-text-dim"
            }`}
          >
            Misafir
          </button>
        </div>

        {tab === "email" && step === "enter-email" && (
          <form onSubmit={handleSendCode} className="flex flex-col gap-3">
            <label className="text-left">
              <span className="mb-1.5 block text-xs font-bold text-text-dim">E-posta adresi</span>
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="ornek@eposta.com"
                className="w-full rounded-lg border border-border bg-surface-2 px-3.5 py-2.5 text-sm text-text outline-none focus-visible:ring-2 focus-visible:ring-accent"
              />
            </label>
            {error && <p className="text-sm font-semibold text-loss">{error}</p>}
            <button
              type="submit"
              disabled={busy}
              className="rounded-lg bg-accent py-3 text-sm font-bold text-accent-ink disabled:opacity-50"
            >
              {busy ? "Gönderiliyor…" : "6 haneli kod gönder"}
            </button>
          </form>
        )}

        {tab === "email" && step === "enter-code" && (
          <form onSubmit={handleVerifyCode} className="flex flex-col gap-3">
            <div className="flex items-center gap-2 rounded-lg bg-win/10 px-3 py-2.5 text-sm font-semibold text-win">
              ✓ Kod gönderildi: <span className="font-mono-nums">{email}</span>
            </div>
            <label className="text-left">
              <span className="mb-1.5 block text-xs font-bold text-text-dim">Kodu gir</span>
              <input
                type="text"
                inputMode="numeric"
                maxLength={6}
                required
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
                placeholder="••••••"
                className="w-full rounded-lg border border-border bg-surface-2 px-3.5 py-2.5 text-center font-mono-nums text-lg tracking-[0.5em] text-text outline-none focus-visible:ring-2 focus-visible:ring-accent"
              />
            </label>
            <label className="flex items-center gap-2 text-sm text-text-dim">
              <input
                type="checkbox"
                checked={rememberMe}
                onChange={(e) => setRememberMe(e.target.checked)}
                className="h-4 w-4 accent-accent"
              />
              Bu cihazda beni hatırla
            </label>
            {error && <p className="text-sm font-semibold text-loss">{error}</p>}
            <button
              type="submit"
              disabled={busy}
              className="rounded-lg bg-accent py-3 text-sm font-bold text-accent-ink disabled:opacity-50"
            >
              {busy ? "Doğrulanıyor…" : "Doğrula ve giriş yap"}
            </button>
            <button
              type="button"
              onClick={() => {
                setStep("enter-email");
                setCode("");
                setError(null);
              }}
              className="rounded-lg border border-border py-2.5 text-sm font-bold text-text-dim"
            >
              Farklı bir e-posta kullan
            </button>
          </form>
        )}

        {tab === "guest" && (
          <div className="flex flex-col gap-3 text-center">
            <p className="text-sm text-text-dim">
              Hesap açmadan hemen başla. Çarkı hemen çevirebilirsin — lig sonuçlarını kaydetmek istersen
              profilinden daha sonra giriş yapabilirsin.
            </p>
            <button
              type="button"
              onClick={continueAsGuest}
              className="rounded-lg bg-accent py-3 text-sm font-bold text-accent-ink"
            >
              Misafir olarak devam et
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
