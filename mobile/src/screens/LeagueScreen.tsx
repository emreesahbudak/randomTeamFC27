import { useCallback, useState } from "react";
import { View, Text, ScrollView, Pressable } from "react-native";
import { useNavigation, useFocusEffect } from "@react-navigation/native";
import { leaguesApi, standingsApi, matchesApi, seasonsApi, useAuthStore } from "../lib/auth";
import { logWarn } from "@fc27/shared";
import type { LeagueResponse, StandingRow, LeagueStatsResponse, MatchResponse, SeasonResponse } from "@fc27/shared";

const COLUMNS: { key: keyof StandingRow; label: string }[] = [
  { key: "played", label: "O" },
  { key: "won", label: "G" },
  { key: "drawn", label: "B" },
  { key: "lost", label: "M" },
  { key: "goalsFor", label: "AG" },
  { key: "goalsAgainst", label: "YG" },
  { key: "goalDifference", label: "AV" },
  { key: "points", label: "P" },
];

export function LeagueScreen() {
  const isGuest = useAuthStore((state) => state.isGuest);
  const navigation = useNavigation();

  const [league, setLeague] = useState<LeagueResponse | null>(null);
  const [standings, setStandings] = useState<StandingRow[]>([]);
  const [stats, setStats] = useState<LeagueStatsResponse | null>(null);
  const [matches, setMatches] = useState<MatchResponse[]>([]);
  const [pastSeasons, setPastSeasons] = useState<SeasonResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // useFocusEffect, not a plain useEffect — matches HomeScreen's identical fix: a bottom-tab
  // screen stays mounted when you switch away, so a match registered on the Çark tab would
  // never show up here until refetched on refocus.
  useFocusEffect(
    useCallback(() => {
      if (isGuest) {
        setLoading(false);
        return;
      }
      setLoading(true);
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
    }, [isGuest]),
  );

  if (isGuest) {
    return (
      <View className="flex-1 items-center justify-center bg-bg px-4 dark:bg-bg-dark">
        <Text className="text-center text-sm text-text-faint dark:text-text-faint-dark">
          Puan durumunu görmek için giriş yapmalısın.
        </Text>
      </View>
    );
  }

  if (loading) {
    return (
      <View className="flex-1 items-center justify-center bg-bg dark:bg-bg-dark">
        <Text className="text-sm text-text-faint dark:text-text-faint-dark">Yükleniyor…</Text>
      </View>
    );
  }

  if (loadError) {
    return (
      <View className="flex-1 items-center justify-center bg-bg px-4 dark:bg-bg-dark">
        <Text className="text-center text-sm font-semibold text-loss dark:text-loss-dark">{loadError}</Text>
      </View>
    );
  }

  if (!league) {
    return (
      <View className="flex-1 items-center justify-center gap-2 bg-bg px-4 dark:bg-bg-dark">
        <Text className="text-sm text-text-faint dark:text-text-faint-dark">Henüz bir ligin yok.</Text>
        <Text
          onPress={() => navigation.navigate("Profil" as never)}
          className="text-sm font-bold text-accent dark:text-accent-dark"
        >
          Profilinden bir lig oluştur →
        </Text>
      </View>
    );
  }

  return (
    <ScrollView className="flex-1 bg-bg dark:bg-bg-dark" contentContainerClassName="px-4 py-8">
      <Text className="mb-1 text-[11px] font-bold uppercase tracking-wide text-accent dark:text-accent-dark">Puan Durumu</Text>
      <Text className="mb-6 text-2xl font-bold text-text dark:text-text-dark">{league.name}</Text>

      <View className="mb-6 flex-row gap-3">
        <StatBadge label="Gol Kralı" leader={stats?.topScorer} unit="gol" />
        <StatBadge label="En Az Gol Yiyen" leader={stats?.bestDefense} unit="gol yedi" />
      </View>

      <StandingsTable standings={standings} />

      {stats && (stats.headToHead ?? []).length > 0 && (
        <View className="mb-6 rounded-2xl border border-border-soft bg-surface p-4 dark:border-border-soft-dark dark:bg-surface-dark">
          <Text className="mb-3 text-sm font-bold text-text dark:text-text-dark">Kafa Kafaya</Text>
          {stats.headToHead!.map((h2h) => (
            <View key={`${h2h.player1Id}-${h2h.player2Id}`} className="mb-1 flex-row items-center justify-between">
              <Text className="text-sm text-text-dim dark:text-text-dim-dark">
                {h2h.player1Name} vs {h2h.player2Name}
              </Text>
              <Text className="text-sm font-semibold text-text dark:text-text-dark">
                {h2h.player1Wins}G {h2h.draws}B {h2h.player2Wins}M
              </Text>
            </View>
          ))}
        </View>
      )}

      <View className="mb-6 rounded-2xl border border-border-soft bg-surface p-4 dark:border-border-soft-dark dark:bg-surface-dark">
        <Text className="mb-3 text-sm font-bold text-text dark:text-text-dark">Maç Geçmişi</Text>
        <MatchList matches={matches} />
      </View>

      {pastSeasons.length > 0 && (
        <View>
          <Text className="mb-1 text-[11px] font-bold uppercase tracking-wide text-accent dark:text-accent-dark">Geçmiş Ligler</Text>
          <Text className="mb-3 text-lg font-bold text-text dark:text-text-dark">Önceki Sezonlar</Text>
          <View className="gap-3">
            {pastSeasons.map((season) => (
              <PastSeasonCard key={season.id} leagueId={league.id!} season={season} />
            ))}
          </View>
        </View>
      )}
    </ScrollView>
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
    <View className="rounded-2xl border border-border-soft bg-surface dark:border-border-soft-dark dark:bg-surface-dark">
      <Pressable onPress={toggle} className="flex-row items-center justify-between px-4 py-3">
        <View>
          <Text className="text-sm font-bold text-text dark:text-text-dark">{season.name}</Text>
          <Text className="text-xs text-text-faint dark:text-text-faint-dark">{dateRange}</Text>
        </View>
        <Text className="text-xs font-bold text-accent dark:text-accent-dark">{expanded ? "Gizle ▲" : "Göster ▼"}</Text>
      </Pressable>

      {expanded && (
        <View className="border-t border-border-soft p-4 dark:border-border-soft-dark">
          {loading && <Text className="text-center text-sm text-text-faint dark:text-text-faint-dark">Yükleniyor…</Text>}
          {error && <Text className="text-center text-sm font-semibold text-loss dark:text-loss-dark">{error}</Text>}
          {standings && <StandingsTable standings={standings} compact />}
          {matches && (
            <View className="mt-4">
              <Text className="mb-2 text-xs font-bold uppercase tracking-wide text-text-faint dark:text-text-faint-dark">Maçlar</Text>
              <MatchList matches={matches} />
            </View>
          )}
        </View>
      )}
    </View>
  );
}

function formatDate(value?: Date) {
  if (!value) return "—";
  return value.toLocaleDateString("tr-TR", { day: "numeric", month: "short", year: "numeric" });
}

function StandingsTable({ standings, compact = false }: { standings: StandingRow[]; compact?: boolean }) {
  return (
    <ScrollView
      horizontal
      className={`rounded-2xl border border-border-soft dark:border-border-soft-dark ${compact ? "" : "mb-6"}`}
    >
      <View className="bg-surface dark:bg-surface-dark">
        <View className="flex-row border-b border-border-soft dark:border-border-soft-dark">
          <HeaderCell width={28} label="#" />
          <HeaderCell width={110} label="Oyuncu" />
          {COLUMNS.map((col) => (
            <HeaderCell key={col.key} width={40} label={col.label} />
          ))}
        </View>
        {standings.map((row, index) => (
          <View key={row.playerId} className="flex-row border-b border-border-soft last:border-0 dark:border-border-soft-dark">
            <Cell width={28} text={String(index + 1)} faint />
            <Cell width={110} text={row.displayName ?? "—"} bold />
            {COLUMNS.map((col) => (
              <Cell key={col.key} width={40} text={String(row[col.key] ?? 0)} bold={col.key === "points"} />
            ))}
          </View>
        ))}
        {standings.length === 0 && (
          <Text className="px-3 py-6 text-center text-sm text-text-faint dark:text-text-faint-dark">Henüz oyuncu yok.</Text>
        )}
      </View>
    </ScrollView>
  );
}

function MatchList({ matches }: { matches: MatchResponse[] }) {
  return (
    <View>
      {matches.map((match) => (
        <View key={match.id} className="mb-2 flex-row items-center justify-between">
          <Text className="flex-1 text-xs text-text dark:text-text-dark">
            {match.player1Name} ({match.team1Name}) vs {match.player2Name} ({match.team2Name})
          </Text>
          <Text className="text-sm font-bold text-text dark:text-text-dark">
            {match.score1} - {match.score2}
          </Text>
        </View>
      ))}
      {matches.length === 0 && <Text className="text-center text-sm text-text-faint dark:text-text-faint-dark">Henüz maç kaydı yok.</Text>}
    </View>
  );
}

function HeaderCell({ width, label }: { width: number; label: string }) {
  return (
    <View style={{ width }} className="px-1.5 py-2">
      <Text className="text-[10px] font-bold uppercase text-text-faint dark:text-text-faint-dark">{label}</Text>
    </View>
  );
}

function Cell({ width, text, bold, faint }: { width: number; text: string; bold?: boolean; faint?: boolean }) {
  const colorClass = faint ? "text-text-faint dark:text-text-faint-dark" : "text-text dark:text-text-dark";
  return (
    <View style={{ width }} className="px-1.5 py-2">
      <Text className={`text-xs ${bold ? "font-extrabold" : ""} ${colorClass}`}>{text}</Text>
    </View>
  );
}

function StatBadge({ label, leader, unit }: { label: string; leader?: { displayName?: string; value?: number } | null; unit: string }) {
  return (
    <View className="flex-1 items-center rounded-2xl border border-border-soft bg-surface p-4 dark:border-border-soft-dark dark:bg-surface-dark">
      <Text className="mb-1 text-[10px] font-bold uppercase text-text-faint dark:text-text-faint-dark">{label}</Text>
      {leader ? (
        <>
          <Text className="text-base font-bold text-text dark:text-text-dark">{leader.displayName}</Text>
          <Text className="text-xs text-gold dark:text-gold-dark">
            {leader.value} {unit}
          </Text>
        </>
      ) : (
        <Text className="text-xs text-text-faint dark:text-text-faint-dark">Henüz maç yok</Text>
      )}
    </View>
  );
}
