import { useEffect, useState } from "react";
import { View, Text, TextInput, Pressable, ScrollView } from "react-native";
import { useNavigation } from "@react-navigation/native";
import { authApi, useAuthStore, leaguesApi, playersApi } from "../lib/auth";
import { getRefreshToken, clearRefreshToken } from "../lib/refreshToken";
import { confirmAsync, notify } from "../lib/confirm";
import { logInfo, logWarn, ResponseError } from "@fc27/shared";
import type { LeagueResponse, PlayerResponse } from "@fc27/shared";
import { LoginScreen } from "./LoginScreen";

const ROLE_LABELS: Record<string, string> = {
  ADMIN: "Yönetici",
  USER: "Kullanıcı",
};

async function extractErrorMessage(err: unknown): Promise<string> {
  if (err instanceof ResponseError) {
    try {
      const body = await err.response.json();
      if (typeof body?.message === "string") return body.message;
    } catch {
      // fall through
    }
  }
  return "Bir şeyler ters gitti.";
}

export function ProfileScreen() {
  const { user, isGuest, logout } = useAuthStore();
  const navigation = useNavigation();

  const [league, setLeague] = useState<LeagueResponse | null>(null);
  const [players, setPlayers] = useState<PlayerResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [newLeagueName, setNewLeagueName] = useState("");
  const [creatingLeague, setCreatingLeague] = useState(false);
  const [leagueError, setLeagueError] = useState<string | null>(null);

  const [newPlayerName, setNewPlayerName] = useState("");
  const [addingPlayer, setAddingPlayer] = useState(false);
  const [playerError, setPlayerError] = useState<string | null>(null);

  async function reload() {
    const leagues = await leaguesApi.listMyLeagues();
    const mine = leagues[0] ?? null;
    setLeague(mine);
    setPlayers(mine ? await playersApi.listPlayers({ leagueId: mine.id! }) : []);
  }

  useEffect(() => {
    if (isGuest) {
      setLoading(false);
      return;
    }
    reload()
      .catch((err) => {
        logWarn("profile.loadFailed", { error: String(err) });
        setLoadError("Lig bilgileri yüklenemedi. Sunucu çalışıyor mu?");
      })
      .finally(() => setLoading(false));
  }, [isGuest]);

  async function handleLogout() {
    try {
      const refreshToken = await getRefreshToken();
      await authApi.logout(refreshToken ? { refreshRequest: { refreshToken } } : undefined);
    } catch (error) {
      logInfo("profile.logoutRequestFailed", { error: String(error) });
    }
    await clearRefreshToken();
    logout();
  }

  async function handleCreateLeague() {
    if (!newLeagueName.trim()) return;
    setLeagueError(null);
    setCreatingLeague(true);
    try {
      const created = await leaguesApi.createLeague({ leagueCreateRequest: { name: newLeagueName } });
      setLeague(created);
      setNewLeagueName("");
      logInfo("profile.leagueCreated", { id: created.id });
    } catch (err) {
      setLeagueError(await extractErrorMessage(err));
    } finally {
      setCreatingLeague(false);
    }
  }

  async function handleAddPlayer() {
    if (!league || !newPlayerName.trim()) return;
    setPlayerError(null);
    setAddingPlayer(true);
    try {
      await playersApi.addPlayer({ leagueId: league.id!, playerCreateRequest: { displayName: newPlayerName } });
      setNewPlayerName("");
      await reload();
    } catch (err) {
      setPlayerError(await extractErrorMessage(err));
    } finally {
      setAddingPlayer(false);
    }
  }

  async function handleRemovePlayer(player: PlayerResponse) {
    if (!league) return;
    const confirmed = await confirmAsync(`${player.displayName} ligden çıkarılsın mı?`, "Geçmiş maçları korunacak.", "Çıkar");
    if (!confirmed) return;
    try {
      await playersApi.removePlayer({ leagueId: league.id!, playerId: player.id! });
      await reload();
    } catch (err) {
      logWarn("profile.removePlayerFailed", { error: String(err) });
      notify("Hata", await extractErrorMessage(err));
    }
  }

  async function handleResetLeague() {
    if (!league) return;
    const confirmed = await confirmAsync(
      "Ligdeki tüm maçlar silinsin mi?",
      "Oyuncular kalır, puan durumu sıfırlanır. Bu işlem geri alınamaz.",
      "Sıfırla",
    );
    if (!confirmed) return;
    try {
      await leaguesApi.resetLeague({ id: league.id! });
      logInfo("profile.leagueReset", { id: league.id });
      notify("Lig sıfırlandı.");
    } catch (err) {
      logWarn("profile.resetLeagueFailed", { error: String(err) });
      notify("Hata", await extractErrorMessage(err));
    }
  }

  if (isGuest) {
    // "Continue as guest" doesn't change isGuest, so this screen would just show the same
    // LoginScreen again with nothing new — send that path to Çark. A real login flips
    // isGuest to false, and ProfileScreen re-renders its own (now much more useful) league
    // management UI below — navigating away from it would defeat the point of having
    // logged in from this tab in the first place.
    return (
      <LoginScreen
        onSuccess={() => {
          if (useAuthStore.getState().isGuest) {
            navigation.navigate("Çark" as never);
          }
        }}
      />
    );
  }

  return (
    <ScrollView className="flex-1 bg-bg dark:bg-bg-dark" contentContainerClassName="px-4 py-8">
      <Text className="mb-1 text-[11px] font-bold uppercase tracking-wide text-accent dark:text-accent-dark">Hesap</Text>
      <Text className="mb-6 text-2xl font-bold text-text dark:text-text-dark">Profil</Text>

      <View className="rounded-2xl border border-border-soft bg-surface p-6 dark:border-border-soft-dark dark:bg-surface-dark">
        <Text className="mb-4 text-base font-bold text-text dark:text-text-dark">Kişisel bilgiler</Text>
        <View className="gap-4">
          <View>
            <Text className="mb-1 text-xs font-bold text-text-dim dark:text-text-dim-dark">Görünen ad</Text>
            <Text className="text-sm text-text dark:text-text-dark">{user?.displayName}</Text>
          </View>
          <View>
            <Text className="mb-1 text-xs font-bold text-text-dim dark:text-text-dim-dark">E-posta</Text>
            <Text className="text-sm text-text dark:text-text-dark">{user?.email ?? "—"}</Text>
          </View>
          <View>
            <Text className="mb-1 text-xs font-bold text-text-dim dark:text-text-dim-dark">Rol</Text>
            <Text className="text-sm text-text dark:text-text-dark">{user ? (ROLE_LABELS[user.role] ?? user.role) : "—"}</Text>
          </View>
        </View>
      </View>

      <View className="mt-4 rounded-2xl border border-border-soft bg-surface p-6 dark:border-border-soft-dark dark:bg-surface-dark">
        <Text className="mb-1 text-base font-bold text-text dark:text-text-dark">Lig Yönetimi</Text>

        {loading && <Text className="text-sm text-text-faint dark:text-text-faint-dark">Yükleniyor…</Text>}
        {loadError && <Text className="text-sm font-semibold text-loss dark:text-loss-dark">{loadError}</Text>}

        {!loading && !loadError && !league && (
          <View className="gap-2">
            <Text className="mb-1 text-sm text-text-faint dark:text-text-faint-dark">
              Henüz bir ligin yok. Arkadaş grubunla maç takibi yapmak için bir lig oluştur.
            </Text>
            <TextInput
              value={newLeagueName}
              onChangeText={setNewLeagueName}
              placeholder="Lig adı, örn. Cuma Gecesi"
              className="rounded-lg border border-border bg-surface-2 px-3 py-2 text-sm text-text dark:border-border-dark dark:bg-surface-2-dark dark:text-text-dark"
            />
            <Pressable
              onPress={handleCreateLeague}
              disabled={creatingLeague}
              className="items-center rounded-lg bg-accent py-2.5 dark:bg-accent-dark"
              style={{ opacity: creatingLeague ? 0.5 : 1 }}
            >
              <Text className="text-sm font-bold text-accent-ink dark:text-accent-ink-dark">
                {creatingLeague ? "Oluşturuluyor…" : "Oluştur"}
              </Text>
            </Pressable>
            {leagueError && <Text className="text-sm font-semibold text-loss dark:text-loss-dark">{leagueError}</Text>}
          </View>
        )}

        {!loading && !loadError && league && (
          <View className="gap-4">
            <View className="flex-row items-center justify-between">
              <Text className="text-sm font-bold text-text dark:text-text-dark">{league.name}</Text>
              <Pressable onPress={() => navigation.navigate("Lig" as never)}>
                <Text className="text-xs font-bold text-accent dark:text-accent-dark">Lig tablosunu gör →</Text>
              </Pressable>
            </View>

            <View>
              <Text className="mb-2 text-xs font-bold uppercase tracking-wide text-text-faint dark:text-text-faint-dark">
                Oyuncular ({players.length})
              </Text>
              <View className="rounded-xl border border-border-soft dark:border-border-soft-dark">
                {players.map((player, index) => (
                  <View
                    key={player.id}
                    className="flex-row items-center justify-between px-3 py-2"
                    style={index > 0 ? { borderTopWidth: 1, borderTopColor: "rgba(128,128,128,0.2)" } : undefined}
                  >
                    <Text className="text-sm text-text dark:text-text-dark">{player.displayName}</Text>
                    <Pressable onPress={() => handleRemovePlayer(player)}>
                      <Text className="text-xs font-bold text-loss dark:text-loss-dark">Çıkar</Text>
                    </Pressable>
                  </View>
                ))}
                {players.length === 0 && (
                  <Text className="px-3 py-3 text-center text-sm text-text-faint dark:text-text-faint-dark">Henüz oyuncu yok.</Text>
                )}
              </View>
              <View className="mt-2 gap-2">
                <TextInput
                  value={newPlayerName}
                  onChangeText={setNewPlayerName}
                  placeholder="Oyuncu adı, örn. Ahmet"
                  className="rounded-lg border border-border bg-surface-2 px-3 py-2 text-sm text-text dark:border-border-dark dark:bg-surface-2-dark dark:text-text-dark"
                />
                <Pressable
                  onPress={handleAddPlayer}
                  disabled={addingPlayer}
                  className="items-center rounded-lg bg-accent py-2.5 dark:bg-accent-dark"
                  style={{ opacity: addingPlayer ? 0.5 : 1 }}
                >
                  <Text className="text-sm font-bold text-accent-ink dark:text-accent-ink-dark">
                    {addingPlayer ? "Ekleniyor…" : "+ Oyuncu ekle"}
                  </Text>
                </Pressable>
              </View>
              {playerError && <Text className="mt-2 text-sm font-semibold text-loss dark:text-loss-dark">{playerError}</Text>}
            </View>

            <View className="rounded-xl border border-loss/30 bg-loss/5 p-3">
              <Text className="mb-1 text-xs font-bold uppercase tracking-wide text-loss dark:text-loss-dark">Tehlikeli Bölge</Text>
              <Text className="mb-2 text-xs text-text-faint dark:text-text-faint-dark">
                Ligdeki tüm maç kayıtlarını siler; oyuncular ve lig kalır. Geri alınamaz.
              </Text>
              <Pressable onPress={handleResetLeague} className="items-center rounded-lg border border-loss px-3 py-1.5 dark:border-loss-dark">
                <Text className="text-xs font-bold text-loss dark:text-loss-dark">Ligi sıfırla</Text>
              </Pressable>
            </View>
          </View>
        )}
      </View>

      <Pressable onPress={handleLogout} className="mt-6 items-center rounded-lg border border-loss py-2.5 dark:border-loss-dark">
        <Text className="text-sm font-bold text-loss dark:text-loss-dark">Çıkış yap</Text>
      </Pressable>
    </ScrollView>
  );
}
