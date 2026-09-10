import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { leaguesApi, standingsApi, matchesApi, seasonsApi } from "../lib/auth";
import { logWarn } from "@fc27/shared";
import type { LeagueResponse, StandingRow, LeagueStatsResponse, MatchResponse, SeasonResponse } from "@fc27/shared";

export function LeaguePage() {
  const [league, setLeague] = useState<LeagueResponse | null>(null);
  const [standings, setStandings] = useState<StandingRow[]>([]);
  const [stats, setStats] = useState<LeagueStatsResponse | null>(null);
  const [matches, setMatches] = useState<MatchResponse[]>([]);
  const [pastSeasons, setPastSeasons] = useState<SeasonResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      const leagues = await leaguesApi.listMyLeagues();
      const mine = leagues[0];
      if (!mine) return;
      setLeague(mine);
      const [standingsResult, statsResult, matchesResult, pastSeasonsResult] = await Promise.all([
        standingsApi.getStandings({ leagueId: mine.id! }),
        standingsApi.getLeagueStats({ leagueId: mine.id! }),
        matchesApi.listMatches({ leagueId: mine.id! }),
        seasonsApi.listPastSeasons({ leagueId: mine.id! }),
      ]);
      setStandings(standingsResult);
      setStats(statsResult);
      setMatches(matchesResult);
      setPastSeasons(pastSeasonsResult);
    })()
      .catch((err) => {
        logWarn("league.loadFailed", { error: String(err) });
        setLoadError("Lig tablosu yüklenemedi. Sunucu çalışıyor mu?");
      })
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return <div className="px-4 py-8 text-center text-sm text-text-faint">Yükleniyor…</div>;
  }

  if (loadError) {
    return <div className="px-4 py-8 text-center text-sm font-semibold text-loss">{loadError}</div>;
  }

  if (!league) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-8 text-center sm:px-6">
        <p className="mb-3 text-sm text-text-faint">Henüz bir ligin yok.</p>
        <Link to="/profile" className="text-sm font-bold text-accent hover:underline">
          Profilinden bir lig oluştur →
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6">
      <span className="mb-1 block text-[11px] font-bold uppercase tracking-wide text-accent">Puan Durumu</span>
      <h1 className="mb-6 text-2xl font-bold text-text">{league.name}</h1>

      <div className="mb-6 grid grid-cols-1 gap-3 sm:grid-cols-2">
        <StatBadge label="Gol Kralı" leader={stats?.topScorer} unit="gol" />
        <StatBadge label="En Az Gol Yiyen" leader={stats?.bestDefense} unit="gol yedi" />
      </div>

      <StandingsTable standings={standings} />

      {stats && (stats.headToHead ?? []).length > 0 && (
        <div className="mb-6 rounded-2xl border border-border-soft bg-surface p-4">
          <h2 className="mb-3 text-sm font-bold text-text">Kafa Kafaya</h2>
          <div className="flex flex-col gap-2">
            {stats.headToHead!.map((h2h) => (
              <div key={`${h2h.player1Id}-${h2h.player2Id}`} className="flex items-center justify-between text-sm">
                <span className="text-text-dim">
                  {h2h.player1Name} vs {h2h.player2Name}
                </span>
                <span className="font-mono-nums font-semibold text-text">
                  {h2h.player1Wins}G {h2h.draws}B {h2h.player2Wins}M
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="mb-6 rounded-2xl border border-border-soft bg-surface p-4">
        <h2 className="mb-3 text-sm font-bold text-text">Maç Geçmişi</h2>
        <MatchList matches={matches} />
      </div>

      {pastSeasons.length > 0 && (
        <div>
          <span className="mb-1 block text-[11px] font-bold uppercase tracking-wide text-accent">Geçmiş Ligler</span>
          <h2 className="mb-3 text-lg font-bold text-text">Önceki Sezonlar</h2>
          <div className="flex flex-col gap-3">
            {pastSeasons.map((season) => (
              <PastSeasonCard key={season.id} leagueId={league.id!} season={season} />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function PastSeasonCard({ leagueId, season }: { leagueId: number; season: SeasonResponse }) {
  const [expanded, setExpanded] = useState(false);
  const [standings, setStandings] = useState<StandingRow[] | null>(null);
  const [matches, setMatches] = useState<MatchResponse[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function toggle() {
    if (expanded) {
      setExpanded(false);
      return;
    }
    setExpanded(true);
    if (standings !== null) return; // already loaded once
    setLoading(true);
    setError(null);
    try {
      const [standingsResult, matchesResult] = await Promise.all([
        seasonsApi.getPastSeasonStandings({ leagueId, seasonId: season.id! }),
        seasonsApi.getPastSeasonMatches({ leagueId, seasonId: season.id! }),
      ]);
      setStandings(standingsResult);
      setMatches(matchesResult);
    } catch (err) {
      logWarn("league.pastSeasonLoadFailed", { error: String(err) });
      setError("Sezon bilgileri yüklenemedi.");
    } finally {
      setLoading(false);
    }
  }

  const dateRange = `${formatDate(season.startedAt)} — ${formatDate(season.endedAt)}`;

  return (
    <div className="rounded-2xl border border-border-soft bg-surface">
      <button
        type="button"
        onClick={toggle}
        className="flex w-full items-center justify-between px-4 py-3 text-left"
      >
        <div>
          <div className="text-sm font-bold text-text">{season.name}</div>
          <div className="text-xs text-text-faint">{dateRange}</div>
        </div>
        <span className="text-xs font-bold text-accent">{expanded ? "Gizle ▲" : "Göster ▼"}</span>
      </button>

      {expanded && (
        <div className="border-t border-border-soft px-4 py-4">
          {loading && <p className="text-center text-sm text-text-faint">Yükleniyor…</p>}
          {error && <p className="text-center text-sm font-semibold text-loss">{error}</p>}
          {standings && <StandingsTable standings={standings} compact />}
          {matches && (
            <div className="mt-4">
              <h3 className="mb-2 text-xs font-bold uppercase tracking-wide text-text-faint">Maçlar</h3>
              <MatchList matches={matches} />
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function formatDate(value?: Date) {
  if (!value) return "—";
  return value.toLocaleDateString("tr-TR", { day: "numeric", month: "short", year: "numeric" });
}

function StandingsTable({ standings, compact = false }: { standings: StandingRow[]; compact?: boolean }) {
  return (
    <div className={`overflow-x-auto rounded-2xl border border-border-soft bg-surface ${compact ? "" : "mb-6"}`}>
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border-soft text-left text-[11px] font-bold uppercase tracking-wide text-text-faint">
            <th className="px-3 py-2">#</th>
            <th className="px-3 py-2">Oyuncu</th>
            <th className="px-3 py-2 text-center">O</th>
            <th className="px-3 py-2 text-center">G</th>
            <th className="px-3 py-2 text-center">B</th>
            <th className="px-3 py-2 text-center">M</th>
            <th className="px-3 py-2 text-center">AG</th>
            <th className="px-3 py-2 text-center">YG</th>
            <th className="px-3 py-2 text-center">AV</th>
            <th className="px-3 py-2 text-center font-extrabold text-text">P</th>
          </tr>
        </thead>
        <tbody>
          {standings.map((row, index) => (
            <tr key={row.playerId} className="border-b border-border-soft last:border-0">
              <td className="px-3 py-2 text-text-faint">{index + 1}</td>
              <td className="px-3 py-2 font-semibold text-text">{row.displayName}</td>
              <td className="px-3 py-2 text-center font-mono-nums">{row.played}</td>
              <td className="px-3 py-2 text-center font-mono-nums">{row.won}</td>
              <td className="px-3 py-2 text-center font-mono-nums">{row.drawn}</td>
              <td className="px-3 py-2 text-center font-mono-nums">{row.lost}</td>
              <td className="px-3 py-2 text-center font-mono-nums">{row.goalsFor}</td>
              <td className="px-3 py-2 text-center font-mono-nums">{row.goalsAgainst}</td>
              <td className="px-3 py-2 text-center font-mono-nums">{row.goalDifference}</td>
              <td className="px-3 py-2 text-center font-mono-nums font-extrabold text-text">{row.points}</td>
            </tr>
          ))}
          {standings.length === 0 && (
            <tr>
              <td colSpan={10} className="px-3 py-6 text-center text-text-faint">
                Henüz oyuncu yok.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

function MatchList({ matches }: { matches: MatchResponse[] }) {
  return (
    <div className="flex flex-col divide-y divide-border-soft">
      {matches.map((match) => (
        <div key={match.id} className="flex items-center justify-between py-2 text-sm">
          <span className="text-text">
            {match.player1Name} ({match.team1Name}) vs {match.player2Name} ({match.team2Name})
          </span>
          <span className="font-mono-nums font-bold text-text">
            {match.score1} - {match.score2}
          </span>
        </div>
      ))}
      {matches.length === 0 && <p className="py-6 text-center text-sm text-text-faint">Henüz maç kaydı yok.</p>}
    </div>
  );
}

function StatBadge({ label, leader, unit }: { label: string; leader?: { displayName?: string; value?: number } | null; unit: string }) {
  return (
    <div className="rounded-2xl border border-border-soft bg-surface p-4 text-center">
      <div className="mb-1 text-[11px] font-bold uppercase tracking-wide text-text-faint">{label}</div>
      {leader ? (
        <>
          <div className="text-lg font-bold text-text">{leader.displayName}</div>
          <div className="text-xs text-gold">
            {leader.value} {unit}
          </div>
        </>
      ) : (
        <div className="text-sm text-text-faint">Henüz maç yok</div>
      )}
    </div>
  );
}
