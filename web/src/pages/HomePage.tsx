import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { teamsApi, leagueTypesApi, leaguesApi, playersApi, matchesApi, useAuthStore } from "../lib/auth";
import { filterTeams, WheelError, logWarn, logInfo, ResponseError, type WheelTeam } from "@fc27/shared";
import type { TeamResponse, LeagueTypeResponse, LeagueResponse, PlayerResponse } from "@fc27/shared";
import { TeamCrest } from "../components/TeamCrest";

const STAR_LEVELS = [2, 3, 4, 5];
const SPIN_DURATION_MS = 900;
const SPIN_TICK_MS = 90;
// How long the drawn matchup stays on the wheel before it's swept into the "Maç Kaydı"
// card and the wheel/selectors reset for the next pair — long enough to actually read it.
const RESULT_HOLD_MS = 2500;

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

    setSpinningPlayer(2);
    await animateReveal(pool, setPlayer2, player2Team);
    if (spinToken.current !== token) return;

    setSpinningPlayer(null);

    if (assignedPlayers) {
      // Let players actually see who they were matched with before it's swept away.
      await sleep(RESULT_HOLD_MS);
      if (spinToken.current !== token) return; // a newer spin started during the hold — abandon this one

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

        {canAssignPlayers && (
          <div className="mb-5 flex flex-wrap items-center gap-2.5 border-b border-dashed border-border pb-5">
            <span className="text-[11px] font-bold uppercase tracking-wide text-text-faint">Ligden oyuncu seç</span>
            <select
              value={selectedPlayer1Id}
              onChange={(e) => setSelectedPlayer1Id(e.target.value ? Number(e.target.value) : "")}
              className="rounded-full border border-border bg-surface-2 px-3.5 py-1.5 text-xs font-semibold text-text"
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
            <span className="text-xs text-text-faint">vs</span>
            <select
              value={selectedPlayer2Id}
              onChange={(e) => setSelectedPlayer2Id(e.target.value ? Number(e.target.value) : "")}
              className="rounded-full border border-border bg-surface-2 px-3.5 py-1.5 text-xs font-semibold text-text"
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
          </div>
        )}

        <div className="grid grid-cols-1 items-center gap-4 sm:grid-cols-[1fr_auto_1fr]">
          <PlayerPanel label={selectedPlayer1?.displayName ?? "Oyuncu 1"} team={player1} spinning={spinningPlayer === 1} />

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

          <PlayerPanel label={selectedPlayer2?.displayName ?? "Oyuncu 2"} team={player2} spinning={spinningPlayer === 2} />
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
    </div>
  );
}

function PlayerPanel({ label, team, spinning }: { label: string; team: WheelTeam | null; spinning: boolean }) {
  return (
    <div className="relative flex min-h-[190px] flex-col items-center justify-center gap-3 rounded-2xl border border-border bg-surface-2 p-5 text-center">
      <span className="absolute left-3.5 top-3 text-[10px] font-extrabold uppercase tracking-wide text-text-faint">
        {label}
      </span>
      <TeamCrest code={team?.code} colorHex={team?.colorHex} spinning={spinning} />
      <div>
        <div className="font-display text-base font-bold text-text">{team?.name ?? "—"}</div>
        {team && <div className="mt-1 text-xs text-gold">{"★".repeat(team.starLevel)}</div>}
      </div>
    </div>
  );
}
