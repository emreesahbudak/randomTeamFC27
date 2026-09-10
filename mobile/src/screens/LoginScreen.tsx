import { useState } from "react";
import { View, Text, TextInput, Pressable, Switch } from "react-native";
import { useColorScheme } from "nativewind";
import { authApi, useAuthStore } from "../lib/auth";
import { setRefreshToken } from "../lib/refreshToken";
import { logInfo, logWarn, ResponseError } from "@fc27/shared";

type Tab = "email" | "guest";
type EmailStep = "enter-email" | "enter-code";

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

// Active-tab background/text colors applied via `style` (not a conditionally-swapped
// className) — see TeamCrest.tsx's comment on why NativeWind (Fabric) needs this.
const TAB_COLORS = {
  light: { activeBg: "#ffffff", activeText: "#0f1713", inactiveText: "#54625c" },
  dark: { activeBg: "#121a17", activeText: "#eaf2ed", inactiveText: "#8fa79c" },
};

export function LoginScreen({ onSuccess }: { onSuccess: () => void }) {
  const login = useAuthStore((state) => state.login);
  const { colorScheme } = useColorScheme();
  const tabColors = colorScheme === "dark" ? TAB_COLORS.dark : TAB_COLORS.light;
  const [tab, setTab] = useState<Tab>("email");

  const [step, setStep] = useState<EmailStep>("enter-email");
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [rememberMe, setRememberMe] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function handleSendCode() {
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

  async function handleVerifyCode() {
    setError(null);
    setBusy(true);
    try {
      const response = await authApi.verifyEmailOtp({ emailOtpVerifyRequest: { email, code, rememberMe } });
      if (!response.accessToken || !response.refreshToken || !response.user) {
        throw new Error("Malformed login response");
      }
      // "Remember me" only controls the web-side cookie; mobile always persists the
      // refresh token in SecureStore (there's no cookie concept to gate it behind) so
      // auto-login on app restart works the same as when web sets that cookie.
      await setRefreshToken(response.refreshToken);
      login(response.accessToken, {
        id: response.user.id!,
        displayName: response.user.displayName!,
        email: response.user.email,
        phone: response.user.phone,
        role: (response.user.role as "USER" | "ADMIN") ?? "USER",
      });
      onSuccess();
    } catch (err) {
      logWarn("login.otpVerifyFailed", { error: String(err) });
      setError(await extractErrorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  function continueAsGuest() {
    logInfo("login.continueAsGuest");
    onSuccess();
  }

  return (
    <View className="flex-1 bg-bg px-4 py-10 dark:bg-bg-dark">
      <View className="rounded-2xl border border-border-soft bg-surface p-6 dark:border-border-soft-dark dark:bg-surface-dark">
        <View className="mb-6 items-center">
          <View className="mb-3 h-14 w-14 items-center justify-center rounded-2xl bg-accent dark:bg-accent-dark">
            <Text className="text-lg font-bold text-accent-ink dark:text-accent-ink-dark">27</Text>
          </View>
          <Text className="text-xl font-bold text-text dark:text-text-dark">Tekrar hoş geldin</Text>
          <Text className="mt-1 text-center text-sm text-text-faint dark:text-text-faint-dark">
            Takım çekmek ve ligini takip etmek için giriş yap
          </Text>
        </View>

        <View className="mb-5 flex-row gap-1.5 rounded-xl bg-surface-2 p-1 dark:bg-surface-2-dark">
          <Pressable
            onPress={() => setTab("email")}
            className="flex-1 items-center rounded-lg py-2.5"
            style={{ backgroundColor: tab === "email" ? tabColors.activeBg : "transparent" }}
          >
            <Text className="text-sm font-bold" style={{ color: tab === "email" ? tabColors.activeText : tabColors.inactiveText }}>
              E-posta
            </Text>
          </Pressable>
          <Pressable
            onPress={() => setTab("guest")}
            className="flex-1 items-center rounded-lg py-2.5"
            style={{ backgroundColor: tab === "guest" ? tabColors.activeBg : "transparent" }}
          >
            <Text className="text-sm font-bold" style={{ color: tab === "guest" ? tabColors.activeText : tabColors.inactiveText }}>
              Misafir
            </Text>
          </Pressable>
        </View>

        {tab === "email" && step === "enter-email" && (
          <View className="gap-3">
            <View>
              <Text className="mb-1.5 text-xs font-bold text-text-dim dark:text-text-dim-dark">E-posta adresi</Text>
              <TextInput
                value={email}
                onChangeText={setEmail}
                placeholder="ornek@eposta.com"
                keyboardType="email-address"
                autoCapitalize="none"
                className="rounded-lg border border-border bg-surface-2 px-3.5 py-2.5 text-sm text-text dark:border-border-dark dark:bg-surface-2-dark dark:text-text-dark"
              />
            </View>
            {error && <Text className="text-sm font-semibold text-loss dark:text-loss-dark">{error}</Text>}
            <Pressable
              onPress={handleSendCode}
              disabled={busy || !email}
              className="items-center rounded-lg bg-accent py-3 dark:bg-accent-dark"
              style={{ opacity: busy || !email ? 0.5 : 1 }}
            >
              <Text className="text-sm font-bold text-accent-ink dark:text-accent-ink-dark">
                {busy ? "Gönderiliyor…" : "6 haneli kod gönder"}
              </Text>
            </Pressable>
          </View>
        )}

        {tab === "email" && step === "enter-code" && (
          <View className="gap-3">
            <View className="rounded-lg bg-win/10 px-3 py-2.5">
              <Text className="text-sm font-semibold text-win dark:text-win-dark">✓ Kod gönderildi: {email}</Text>
            </View>
            <View>
              <Text className="mb-1.5 text-xs font-bold text-text-dim dark:text-text-dim-dark">Kodu gir</Text>
              <TextInput
                value={code}
                onChangeText={(text) => setCode(text.replace(/\D/g, ""))}
                placeholder="••••••"
                keyboardType="number-pad"
                maxLength={6}
                className="rounded-lg border border-border bg-surface-2 px-3.5 py-2.5 text-center text-lg tracking-[8px] text-text dark:border-border-dark dark:bg-surface-2-dark dark:text-text-dark"
              />
            </View>
            <View className="flex-row items-center gap-2">
              <Switch value={rememberMe} onValueChange={setRememberMe} accessibilityLabel="Bu cihazda beni hatırla" />
              <Text className="text-sm text-text-dim dark:text-text-dim-dark">Bu cihazda beni hatırla</Text>
            </View>
            {error && <Text className="text-sm font-semibold text-loss dark:text-loss-dark">{error}</Text>}
            <Pressable
              onPress={handleVerifyCode}
              disabled={busy || code.length !== 6}
              className="items-center rounded-lg bg-accent py-3 dark:bg-accent-dark"
              style={{ opacity: busy || code.length !== 6 ? 0.5 : 1 }}
            >
              <Text className="text-sm font-bold text-accent-ink dark:text-accent-ink-dark">
                {busy ? "Doğrulanıyor…" : "Doğrula ve giriş yap"}
              </Text>
            </Pressable>
            <Pressable
              onPress={() => {
                setStep("enter-email");
                setCode("");
                setError(null);
              }}
              className="items-center rounded-lg border border-border py-2.5 dark:border-border-dark"
            >
              <Text className="text-sm font-bold text-text-dim dark:text-text-dim-dark">Farklı bir e-posta kullan</Text>
            </Pressable>
          </View>
        )}

        {tab === "guest" && (
          <View className="gap-3">
            <Text className="text-center text-sm text-text-dim dark:text-text-dim-dark">
              Hesap açmadan hemen başla. Çarkı hemen çevirebilirsin — lig sonuçlarını kaydetmek istersen profilinden
              daha sonra giriş yapabilirsin.
            </Text>
            <Pressable onPress={continueAsGuest} className="items-center rounded-lg bg-accent py-3 dark:bg-accent-dark">
              <Text className="text-sm font-bold text-accent-ink dark:text-accent-ink-dark">Misafir olarak devam et</Text>
            </Pressable>
          </View>
        )}
      </View>
    </View>
  );
}
