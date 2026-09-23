import { useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from "react";
import { teamsApi, leagueTypesApi, leaguesApi, playersApi, matchesApi, useAuthStore } from "../lib/auth";
import { filterTeams, WheelError, logWarn, logInfo, ResponseError, type WheelTeam } from "@fc27/shared";
import type { TeamResponse, LeagueTypeResponse, LeagueResponse, PlayerResponse } from "@fc27/shared";
import { TeamCrest } from "../components/TeamCrest";

const STAR_LEVELS = [2, 3, 4, 5];
const SPIN_DURATION_MS = 1300;
const SPIN_TICK_MS = 90;
// How long each player's drawn team "pops" forward and holds before settling back into
// place — long enough to actually read it, baked into each reveal rather than one big
// pause at the end (see handleSpin).
const POP_HOLD_MS = 2000;

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

function toWheelTeam(team: TeamResponse): WheelTeam | null {
  if (team.id == null || !team.name || !team.code || team.starLevel == null || team.leagueTypeId == null) {
    return null;
  }
  return {
    id: team.id,
    name: team.name,
    code: team.code,
    starLevel: team.starLevel,
    leagueTypeId: team.leagueTypeId,
    crestUrl: team.crestUrl,
    colorHex: team.colorHex,
  };
}

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

/** Rapidly cycles `onTick` through random teams from the pool for a "slot machine" effect,
 *  then settles on `finalTeam`. Purely cosmetic — the real pick already happened. */
function animateReveal(pool: WheelTeam[], onTick: (team: WheelTeam) => void, finalTeam: WheelTeam): Promise<void> {
  return new Promise((resolve) => {
    const totalTicks = Math.floor(SPIN_DURATION_MS / SPIN_TICK_MS);
    let tick = 0;
    const interval = window.setInterval(() => {
      tick += 1;
      if (tick >= totalTicks) {
        window.clearInterval(interval);
        onTick(finalTeam);
        resolve();
      } else {
        onTick(pool[Math.floor(Math.random() * pool.length)]);
      }
    }, SPIN_TICK_MS);
  });
}

export function HomePage() {
  const isGuest = useAuthStore((state) => state.isGuest);

  const [teams, setTeams] = useState<WheelTeam[]>([]);
  const [leagueTypes, setLeagueTypes] = useState<LeagueTypeResponse[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const [selectedStars, setSelectedStars] = useState<number[]>([]);
  const [selectedLeagueTypeId, setSelectedLeagueTypeId] = useState<number | undefined>(undefined);
  const [mirrorMatch, setMirrorMatch] = useState(false);

  const [player1, setPlayer1] = useState<WheelTeam | null>(null);
  const [player2, setPlayer2] = useState<WheelTeam | null>(null);
  const [spinningPlayer, setSpinningPlayer] = useState<1 | 2 | null>(null);
  // Which panel is currently "popped" forward showing its just-drawn team — see handleSpin.
  const [poppedPlayer, setPoppedPlayer] = useState<1 | 2 | null>(null);
  const [spinError, setSpinError] = useState<string | null>(null);
  const spinToken = useRef(0);

  // Single-league assumption for now (see project notes): a logged-in user's one league's
  // roster can be assigned into the wheel's Player 1/2 slots before spinning. Once the spin
  // settles, that result is captured into `pendingMatch` (a frozen snapshot) and the wheel +
  // player pickers reset immediately, ready for the next pair — the registration card below
  // renders from the snapshot, not from the (now-cleared) live wheel/selection state.
  const [league, setLeague] = useState<LeagueResponse | null>(null);
  const [leaguePlayers, setLeaguePlayers] = useState<PlayerResponse[]>([]);
  const [selectedPlayer1Id, setSelectedPlayer1Id] = useState<number | "">("");
  const [selectedPlayer2Id, setSelectedPlayer2Id] = useState<number | "">("");
  const [pendingMatch, setPendingMatch] = useState<{
    player1Id: number;
    player1Name: string;
    player2Id: number;
    player2Name: string;
    team1: WheelTeam;
    team2: WheelTeam;
  } | null>(null);
  const [score1, setScore1] = useState("0");
  const [score2, setScore2] = useState("0");
  const [registering, setRegistering] = useState(false);
  const [registerError, setRegisterError] = useState<string | null>(null);
  const [registered, setRegistered] = useState(false);

  useEffect(() => {
    let cancelled = false;
    Promise.all([teamsApi.listTeams(), leagueTypesApi.listLeagueTypes()])
      .then(([teamResponses, leagueTypeResponses]) => {
        if (cancelled) return;
        setTeams(teamResponses.map(toWheelTeam).filter((t): t is WheelTeam => t !== null));
        setLeagueTypes(leagueTypeResponses);
      })
      .catch((err) => {
        if (cancelled) return;
        logWarn("home.loadFailed", { error: String(err) });
        setLoadError("Takımlar yüklenemedi. Sunucu çalışıyor mu?");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (isGuest) return;
    let cancelled = false;
    leaguesApi
      .listMyLeagues()
      .then(async (leagues) => {
        if (cancelled) return;
        const mine = leagues[0] ?? null;
        setLeague(mine);
        if (mine) {
          const players = await playersApi.listPlayers({ leagueId: mine.id! });
          if (!cancelled) setLeaguePlayers(players);
        }
      })
      .catch((err) => logWarn("home.leagueLoadFailed", { error: String(err) }));
    return () => {
      cancelled = true;
    };
  }, [isGuest]);

  const filteredPool = useMemo(
    () => filterTeams(teams, { starLevels: selectedStars.length > 0 ? selectedStars : undefined, leagueTypeId: selectedLeagueTypeId }),
    [teams, selectedStars, selectedLeagueTypeId],
  );

  const canAssignPlayers = !isGuest && league !== null && leaguePlayers.length >= 2;
  const selectedPlayer1 = leaguePlayers.find((p) => p.id === selectedPlayer1Id);
  const selectedPlayer2 = leaguePlayers.find((p) => p.id === selectedPlayer2Id);
  // Spinning is blocked until both roster slots are filled whenever a league is active —
  // otherwise the draw would happen without knowing who to register the match for.
  const missingPlayerSelection = canAssignPlayers && (!selectedPlayer1 || !selectedPlayer2);

  function toggleStar(star: number) {
    setSelectedStars((current) =>
      current.includes(star) ? current.filter((s) => s !== star) : [...current, star],
    );
  }

  async function handleSpin() {
    if (pendingMatch) {
      setSpinError("Önce bekleyen maçı kaydet.");
      return;
    }
    if (missingPlayerSelection) {
      setSpinError("Çevirmeden önce iki oyuncuyu da seç.");
      return;
    }
    setSpinError(null);
    setRegistered(false);

    let player1Team: WheelTeam;
    let player2Team: WheelTeam;
    try {
      const pool = filteredPool;
      if (pool.length === 0) {
        throw new WheelError("Filtrelere uyan takım bulunamadı");
      }
      player1Team = pool[Math.floor(Math.random() * pool.length)];
      const player2Candidates = mirrorMatch ? pool : pool.filter((t) => t.id !== player1Team.id);
      if (player2Candidates.length === 0) {
        throw new WheelError("Filtrelere uyan tek takım var — en az iki takım gerekiyor, ya da Aynı Takım seçeneğini aç");
      }
      player2Team = player2Candidates[Math.floor(Math.random() * player2Candidates.length)];
    } catch (err) {
      setSpinError(err instanceof WheelError ? err.message : "Çark çevrilirken bir sorun oluştu.");
      return;
    }

    // Snapshot who's playing before the animation clears the selection out from under it.
    const assignedPlayers = canAssignPlayers && selectedPlayer1 && selectedPlayer2 ? { selectedPlayer1, selectedPlayer2 } : null;

    const token = ++spinToken.current;
    const pool = filteredPool;

    setSpinningPlayer(1);
    await animateReveal(pool, setPlayer1, player1Team);
    if (spinToken.current !== token) return; // a newer spin started — abandon this one

    // Pop Player 1's drawn team forward and hold so it's actually seen before moving on.
    setSpinningPlayer(null);
    setPoppedPlayer(1);
    await sleep(POP_HOLD_MS);
    if (spinToken.current !== token) return;
    setPoppedPlayer(null);

    setSpinningPlayer(2);
    await animateReveal(pool, setPlayer2, player2Team);
    if (spinToken.current !== token) return;

    setSpinningPlayer(null);
    setPoppedPlayer(2);
    await sleep(POP_HOLD_MS);
    if (spinToken.current !== token) return;
    setPoppedPlayer(null);

    if (assignedPlayers) {
      setPendingMatch({
        player1Id: assignedPlayers.selectedPlayer1.id!,
        player1Name: assignedPlayers.selectedPlayer1.displayName!,
        player2Id: assignedPlayers.selectedPlayer2.id!,
        player2Name: assignedPlayers.selectedPlayer2.displayName!,
        team1: player1Team,
        team2: player2Team,
      });
      setPlayer1(null);
      setPlayer2(null);
      setSelectedPlayer1Id("");
      setSelectedPlayer2Id("");
    }
  }

  async function handleRegisterMatch(event: FormEvent) {
    event.preventDefault();
    if (!league || !pendingMatch) return;
    setRegisterError(null);
    setRegistering(true);
    try {
      await matchesApi.recordMatch({
        leagueId: league.id!,
        matchCreateRequest: {
          player1Id: pendingMatch.player1Id,
          player2Id: pendingMatch.player2Id,
          team1Id: pendingMatch.team1.id,
          team2Id: pendingMatch.team2.id,
          score1: Number(score1),
          score2: Number(score2),
        },
      });
      logInfo("home.matchRegistered", { leagueId: league.id });
      setRegistered(true);
      setPendingMatch(null);
      setScore1("0");
      setScore2("0");
    } catch (err) {
      logWarn("home.matchRegisterFailed", { error: String(err) });
      setRegisterError(await extractErrorMessage(err));
    } finally {
      setRegistering(false);
    }
  }

  function handleDiscardMatch() {
    // Nothing was ever sent to the backend — this is purely local "never mind" state, so
    // just drop it and re-enable the wheel. No confirm dialog: it's cheap to redo a spin.
    setPendingMatch(null);
    setRegisterError(null);
    setScore1("0");
    setScore2("0");
  }

  const spinning = spinningPlayer !== null;
  // Locks the player-select dropdowns for the whole draw sequence, including the pop/hold
  // pause between the two reveals — not just while a wheel is actively ticking.
  const selectionLocked = spinning || poppedPlayer !== null || pendingMatch !== null;

  return (
    <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6">
      <div className="mb-5 flex items-baseline justify-between gap-3">
        <div>
          <span className="mb-1 block text-[11px] font-bold uppercase tracking-wide text-accent">
            Maç Çekilişi
          </span>
          <h1 className="text-2xl font-bold text-text">Çark Arenası</h1>
        </div>
        <span className="text-sm text-text-faint">
          {loading ? "Yükleniyor…" : `Havuzda ${filteredPool.length} takım var`}
        </span>
      </div>

      {loadError && (
        <div className="mb-5 rounded-xl border border-loss/30 bg-loss/10 px-4 py-3 text-sm font-semibold text-loss">
          {loadError}
        </div>
      )}

      <div className="rounded-2xl border border-border-soft bg-gradient-to-b from-surface to-bg p-5 sm:p-6">
        <div className="mb-5 flex flex-wrap items-center gap-2.5 border-b border-dashed border-border pb-5">
          <span className="mr-1 text-[11px] font-bold uppercase tracking-wide text-text-faint">Yıldız</span>
          {STAR_LEVELS.map((star) => (
            <button
              key={star}
              type="button"
              onClick={() => toggleStar(star)}
              className={`rounded-lg border px-2.5 py-1.5 font-mono-nums text-xs font-bold ${
                selectedStars.includes(star)
                  ? "border-gold bg-gold/15 text-text"
                  : "border-border bg-surface-2 text-text-dim"
              }`}
            >
              {star}
              <span className="text-gold">★</span>
            </button>
          ))}

          <span className="ml-2 text-[11px] font-bold uppercase tracking-wide text-text-faint">Kategori</span>
          <select
            value={selectedLeagueTypeId ?? "all"}
            onChange={(e) => setSelectedLeagueTypeId(e.target.value === "all" ? undefined : Number(e.target.value))}
            className="rounded-full border border-border bg-surface-2 px-3.5 py-1.5 text-xs font-semibold text-text"
          >
            <option value="all">Tüm Ligler</option>
            {leagueTypes.map((lt) => (
              <option key={lt.id} value={lt.id}>
                {lt.name}
              </option>
            ))}
          </select>

          <label className="ml-auto flex items-center gap-2 text-xs font-semibold text-text-dim">
            Aynı Takım
            <button
              type="button"
              role="switch"
              aria-checked={mirrorMatch}
              onClick={() => setMirrorMatch((m) => !m)}
              className={`relative h-5 w-9 shrink-0 rounded-full border transition-colors ${
                mirrorMatch ? "border-accent-dim bg-accent/25" : "border-border bg-surface-3"
              }`}
            >
              <span
                className={`absolute left-0.5 top-0.5 h-3.5 w-3.5 rounded-full transition-transform ${
                  mirrorMatch ? "translate-x-4 bg-accent" : "translate-x-0 bg-text-dim"
                }`}
              />
            </button>
          </label>
        </div>

        <div className="grid grid-cols-1 items-center gap-4 sm:grid-cols-[1fr_auto_1fr]">
          <PlayerPanel
            label="Oyuncu 1"
            team={player1}
            spinning={spinningPlayer === 1}
            popped={poppedPlayer === 1}
            selectSlot={
              canAssignPlayers && (
                <select
                  value={selectedPlayer1Id}
                  disabled={selectionLocked}
                  onChange={(e) => setSelectedPlayer1Id(e.target.value ? Number(e.target.value) : "")}
                  className="w-full max-w-[11rem] rounded-full border border-border bg-surface-2 px-3 py-1.5 text-center text-xs font-semibold text-text disabled:opacity-60"
                >
                  <option value="">Oyuncu 1…</option>
                  {leaguePlayers
                    .filter((p) => p.id !== selectedPlayer2Id)
                    .map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.displayName}
                      </option>
                    ))}
                </select>
              )
            }
          />

          <div className="flex flex-row items-center justify-center gap-3 sm:flex-col">
            <span className="font-display text-sm font-bold text-text-faint">VS</span>
            <button
              type="button"
              onClick={handleSpin}
              disabled={spinning || loading || teams.length === 0 || missingPlayerSelection || pendingMatch !== null}
              className="flex items-center gap-2 whitespace-nowrap rounded-full bg-gradient-to-br from-accent to-accent-dim px-5 py-3 text-sm font-extrabold text-accent-ink shadow-lg shadow-accent/30 disabled:opacity-50"
            >
              <span className={spinning ? "inline-block animate-spin" : "inline-block"}>◐</span>
              {spinningPlayer === 1 ? "Oyuncu 1 için çevriliyor…" : spinningPlayer === 2 ? "Oyuncu 2 için çevriliyor…" : "Çarkı Çevir"}
            </button>
          </div>

          <PlayerPanel
            label="Oyuncu 2"
            team={player2}
            spinning={spinningPlayer === 2}
            popped={poppedPlayer === 2}
            selectSlot={
              canAssignPlayers && (
                <select
                  value={selectedPlayer2Id}
                  disabled={selectionLocked}
                  onChange={(e) => setSelectedPlayer2Id(e.target.value ? Number(e.target.value) : "")}
                  className="w-full max-w-[11rem] rounded-full border border-border bg-surface-2 px-3 py-1.5 text-center text-xs font-semibold text-text disabled:opacity-60"
                >
                  <option value="">Oyuncu 2…</option>
                  {leaguePlayers
                    .filter((p) => p.id !== selectedPlayer1Id)
                    .map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.displayName}
                      </option>
                    ))}
                </select>
              )
            }
          />
        </div>

        {spinError && <p className="mt-4 text-center text-sm font-semibold text-loss">{spinError}</p>}
      </div>

      {pendingMatch && (
        <form onSubmit={handleRegisterMatch} className="mt-5 rounded-2xl border border-border-soft bg-surface p-5 sm:p-6">
          <span className="mb-1 block text-[11px] font-bold uppercase tracking-wide text-accent">Maç Kaydı</span>
          <h2 className="mb-4 text-base font-bold text-text">
            {pendingMatch.player1Name} ({pendingMatch.team1.name}) vs {pendingMatch.player2Name} ({pendingMatch.team2.name})
          </h2>
          <div className="flex items-center justify-center gap-4">
            <input
              type="number"
              min={0}
              value={score1}
              onChange={(e) => setScore1(e.target.value)}
              className="w-16 rounded-lg border border-border bg-surface-2 px-3 py-2 text-center text-lg font-bold text-text"
            />
            <span className="text-text-faint">-</span>
            <input
              type="number"
              min={0}
              value={score2}
              onChange={(e) => setScore2(e.target.value)}
              className="w-16 rounded-lg border border-border bg-surface-2 px-3 py-2 text-center text-lg font-bold text-text"
            />
          </div>
          {registerError && <p className="mt-3 text-center text-sm font-semibold text-loss">{registerError}</p>}
          <div className="mt-4 flex items-center justify-center gap-2">
            <button
              type="button"
              onClick={handleDiscardMatch}
              disabled={registering}
              className="rounded-full border border-loss px-5 py-2.5 text-sm font-bold text-loss disabled:opacity-50"
            >
              Eşleşmeyi Sil
            </button>
            <button
              type="submit"
              disabled={registering}
              className="rounded-full bg-accent px-5 py-2.5 text-sm font-bold text-accent-ink disabled:opacity-50"
            >
              {registering ? "Kaydediliyor…" : "Maçı Kaydet"}
            </button>
          </div>
        </form>
      )}

      {registered && (
        <p className="mt-4 text-center text-sm font-semibold text-win">✓ Maç kaydedildi. Bir sonraki maç için tekrar çevirebilirsin.</p>
      )}

      {!isGuest && !league && (
        <p className="mt-6 text-center text-xs text-text-faint">
          Maçları kaydedip puan durumu tutmak için profilinden bir lig oluştur.
        </p>
      )}
      {isGuest && (
        <p className="mt-6 text-center text-xs text-text-faint">
          Giriş yapıp bir lig oluşturursan çektiğin maçları kaydedip puan durumu tutabilirsin.
        </p>
      )}

      <TeamPopOverlay
        team={poppedPlayer === 1 ? player1 : poppedPlayer === 2 ? player2 : null}
        label={
          poppedPlayer === 1
            ? selectedPlayer1?.displayName ?? "Oyuncu 1"
            : poppedPlayer === 2
              ? selectedPlayer2?.displayName ?? "Oyuncu 2"
              : ""
        }
      />
    </div>
  );
}

function PlayerPanel({
  label,
  team,
  spinning,
  popped,
  selectSlot,
}: {
  label: string;
  team: WheelTeam | null;
  spinning: boolean;
  popped: boolean;
  selectSlot: ReactNode;
}) {
  return (
    <div
      className={`relative flex min-h-[190px] flex-col items-center justify-center gap-3 rounded-2xl border bg-surface-2 p-5 text-center transition-colors duration-300 ${
        popped ? "border-accent shadow-lg shadow-accent/20" : "border-border"
      }`}
    >
      <div className="flex h-7 w-full items-center justify-center">
        {selectSlot || (
          <span className="text-[10px] font-extrabold uppercase tracking-wide text-text-faint">{label}</span>
        )}
      </div>
      <TeamCrest code={team?.code} colorHex={team?.colorHex} spinning={spinning} />
      <div>
        <div className="font-display text-base font-bold text-text">{team?.name ?? "—"}</div>
        {team && <div className="mt-1 text-xs text-gold">{"★".repeat(team.starLevel)}</div>}
      </div>
    </div>
  );
}

/** Full-screen centered reveal for a just-drawn team — grows in from the middle of the
 *  screen, holds, then shrinks back out. `team` going from a value to null (rather than
 *  unmounting the component) is what drives the shrink-out transition below. */
function TeamPopOverlay({ team, label }: { team: WheelTeam | null; label: string }) {
  const [displayTeam, setDisplayTeam] = useState<WheelTeam | null>(null);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    if (team) {
      setDisplayTeam(team);
      const raf = requestAnimationFrame(() => setVisible(true));
      return () => cancelAnimationFrame(raf);
    }
    setVisible(false);
    const timeout = window.setTimeout(() => setDisplayTeam(null), 300);
    return () => window.clearTimeout(timeout);
  }, [team]);

  if (!displayTeam) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-bg/70 backdrop-blur-sm">
      <div
        className={`flex flex-col items-center gap-4 rounded-3xl border border-accent/40 bg-surface px-10 py-8 shadow-2xl shadow-accent/30 transition-all duration-300 ease-out ${
          visible ? "scale-100 opacity-100" : "scale-50 opacity-0"
        }`}
      >
        <span className="text-xs font-bold uppercase tracking-wide text-text-faint">{label}</span>
        <TeamCrest code={displayTeam.code} colorHex={displayTeam.colorHex} />
        <div className="text-center">
          <div className="font-display text-2xl font-bold text-text">{displayTeam.name}</div>
          <div className="mt-1 text-sm text-gold">{"★".repeat(displayTeam.starLevel)}</div>
        </div>
      </div>
    </div>
  );
}
