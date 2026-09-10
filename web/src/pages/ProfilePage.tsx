import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuthStore, authApi, leaguesApi, playersApi } from "../lib/auth";
import { logInfo, logWarn, ResponseError } from "@fc27/shared";
import type { LeagueResponse, PlayerResponse } from "@fc27/shared";

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

export function ProfilePage() {
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();

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
    // Single-league assumption for now: a user manages at most one league, so we just take
    // the first one listMyLeagues() returns — multi-league selection is a later refinement.
    const leagues = await leaguesApi.listMyLeagues();
    const mine = leagues[0] ?? null;
    setLeague(mine);
    setPlayers(mine ? await playersApi.listPlayers({ leagueId: mine.id! }) : []);
  }

  useEffect(() => {
    reload()
      .catch((err) => {
        logWarn("profile.loadFailed", { error: String(err) });
        setLoadError("Lig bilgileri yüklenemedi. Sunucu çalışıyor mu?");
      })
      .finally(() => setLoading(false));
  }, []);

  async function handleLogout() {
    try {
      await authApi.logout();
    } catch (error) {
      logInfo("profile.logoutRequestFailed", { error: String(error) });
    }
    logout();
    navigate("/login");
  }

  async function handleCreateLeague(event: FormEvent) {
    event.preventDefault();
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

  async function handleAddPlayer(event: FormEvent) {
    event.preventDefault();
    if (!league) return;
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
    if (!confirm(`${player.displayName} ligden çıkarılsın mı? Geçmiş maçları korunacak.`)) return;
    try {
      await playersApi.removePlayer({ leagueId: league.id!, playerId: player.id! });
      await reload();
    } catch (err) {
      logWarn("profile.removePlayerFailed", { error: String(err) });
      alert(await extractErrorMessage(err));
    }
  }

  async function handleResetLeague() {
    if (!league) return;
    if (!confirm("Ligdeki tüm maçlar silinsin mi? Oyuncular kalır, puan durumu sıfırlanır. Bu işlem geri alınamaz.")) return;
    try {
      await leaguesApi.resetLeague({ id: league.id! });
      logInfo("profile.leagueReset", { id: league.id });
      alert("Lig sıfırlandı.");
    } catch (err) {
      logWarn("profile.resetLeagueFailed", { error: String(err) });
      alert(await extractErrorMessage(err));
    }
  }

  return (
    <div className="mx-auto max-w-2xl px-4 py-8 sm:px-6">
      <span className="mb-1 block text-[11px] font-bold uppercase tracking-wide text-accent">Hesap</span>
      <h1 className="mb-6 text-2xl font-bold text-text">Profil</h1>

      <div className="rounded-2xl border border-border-soft bg-surface p-6">
        <h2 className="mb-4 text-base font-bold text-text">Kişisel bilgiler</h2>
        <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <dt className="mb-1 text-xs font-bold text-text-dim">Görünen ad</dt>
            <dd className="text-sm text-text">{user?.displayName}</dd>
          </div>
          <div>
            <dt className="mb-1 text-xs font-bold text-text-dim">E-posta</dt>
            <dd className="text-sm text-text">{user?.email ?? "—"}</dd>
          </div>
          <div>
            <dt className="mb-1 text-xs font-bold text-text-dim">Rol</dt>
            <dd className="text-sm text-text">{user ? (ROLE_LABELS[user.role] ?? user.role) : "—"}</dd>
          </div>
        </dl>
      </div>

      <div className="mt-4 rounded-2xl border border-border-soft bg-surface p-6">
        <h2 className="mb-1 text-base font-bold text-text">Lig Yönetimi</h2>

        {loading && <p className="text-sm text-text-faint">Yükleniyor…</p>}
        {loadError && <p className="text-sm font-semibold text-loss">{loadError}</p>}

        {!loading && !loadError && !league && (
          <>
            <p className="mb-3 text-sm text-text-faint">
              Henüz bir ligin yok. Arkadaş grubunla maç takibi yapmak için bir lig oluştur.
            </p>
            <form onSubmit={handleCreateLeague} className="flex gap-2">
              <input
                value={newLeagueName}
                onChange={(e) => setNewLeagueName(e.target.value)}
                placeholder="Lig adı, örn. Cuma Gecesi"
                required
                className="flex-1 rounded-lg border border-border bg-surface-2 px-3 py-2 text-sm text-text"
              />
              <button
                type="submit"
                disabled={creatingLeague}
                className="rounded-lg bg-accent px-4 text-sm font-bold text-accent-ink disabled:opacity-50"
              >
                {creatingLeague ? "Oluşturuluyor…" : "Oluştur"}
              </button>
            </form>
            {leagueError && <p className="mt-2 text-sm font-semibold text-loss">{leagueError}</p>}
          </>
        )}

        {!loading && !loadError && league && (
          <div className="flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <span className="text-sm font-bold text-text">{league.name}</span>
              <Link to="/league" className="text-xs font-bold text-accent hover:underline">
                Lig tablosunu gör →
              </Link>
            </div>

            <div>
              <h3 className="mb-2 text-xs font-bold uppercase tracking-wide text-text-faint">
                Oyuncular ({players.length})
              </h3>
              <div className="flex flex-col divide-y divide-border-soft rounded-xl border border-border-soft">
                {players.map((player) => (
                  <div key={player.id} className="flex items-center justify-between px-3 py-2">
                    <span className="text-sm text-text">{player.displayName}</span>
                    <button
                      type="button"
                      onClick={() => handleRemovePlayer(player)}
                      className="text-xs font-bold text-loss hover:underline"
                    >
                      Çıkar
                    </button>
                  </div>
                ))}
                {players.length === 0 && (
                  <p className="px-3 py-3 text-center text-sm text-text-faint">Henüz oyuncu yok.</p>
                )}
              </div>
              <form onSubmit={handleAddPlayer} className="mt-2 flex gap-2">
                <input
                  value={newPlayerName}
                  onChange={(e) => setNewPlayerName(e.target.value)}
                  placeholder="Oyuncu adı, örn. Ahmet"
                  required
                  className="flex-1 rounded-lg border border-border bg-surface-2 px-3 py-2 text-sm text-text"
                />
                <button
                  type="submit"
                  disabled={addingPlayer}
                  className="rounded-lg bg-accent px-4 text-sm font-bold text-accent-ink disabled:opacity-50"
                >
                  {addingPlayer ? "Ekleniyor…" : "+ Oyuncu ekle"}
                </button>
              </form>
              {playerError && <p className="mt-2 text-sm font-semibold text-loss">{playerError}</p>}
            </div>

            <div className="rounded-xl border border-loss/30 bg-loss/5 p-3">
              <h3 className="mb-1 text-xs font-bold uppercase tracking-wide text-loss">Tehlikeli Bölge</h3>
              <p className="mb-2 text-xs text-text-faint">
                Ligdeki tüm maç kayıtlarını siler; oyuncular ve lig kalır. Geri alınamaz.
              </p>
              <button
                type="button"
                onClick={handleResetLeague}
                className="rounded-lg border border-loss px-3 py-1.5 text-xs font-bold text-loss"
              >
                Ligi sıfırla
              </button>
            </div>
          </div>
        )}
      </div>

      <button
        type="button"
        onClick={handleLogout}
        className="mt-6 rounded-lg border border-loss px-4 py-2.5 text-sm font-bold text-loss"
      >
        Çıkış yap
      </button>
    </div>
  );
}
