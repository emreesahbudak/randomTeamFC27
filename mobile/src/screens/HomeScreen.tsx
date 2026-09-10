import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { View, Text, Pressable, ScrollView, Switch, ActivityIndicator, TextInput } from "react-native";
import { Picker } from "@react-native-picker/picker";
import { useColorScheme } from "nativewind";
import { useFocusEffect } from "@react-navigation/native";
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
  return new Promise((resolve) => setTimeout(resolve, ms));
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

/** Same cosmetic reveal-cycle as web/src/pages/HomePage.tsx's animateReveal — the real
 *  pick already happened, this just plays a "slot machine" cycle before settling on it. */
function animateReveal(pool: WheelTeam[], onTick: (team: WheelTeam) => void, finalTeam: WheelTeam): Promise<void> {
  return new Promise((resolve) => {
    const totalTicks = Math.floor(SPIN_DURATION_MS / SPIN_TICK_MS);
    let tick = 0;
    const interval = setInterval(() => {
      tick += 1;
      if (tick >= totalTicks) {
        clearInterval(interval);
        onTick(finalTeam);
        resolve();
      } else {
        onTick(pool[Math.floor(Math.random() * pool.length)]);
      }
    }, SPIN_TICK_MS);
  });
}

const PICKER_COLORS = {
  light: { bg: "#f1f5f2", text: "#0f1713" },
  dark: { bg: "#1b2521", text: "#eaf2ed" },
};

// Selected/unselected border+background colors for the star chips, applied via `style`
// (not a conditionally-swapped className) for the same reason as TeamCrest's opacity —
// see the comment there.
const STAR_CHIP_COLORS = {
  light: { selected: { border: "#a5701a", bg: "rgba(165,112,26,0.15)" }, unselected: { border: "#d7ded9", bg: "#f1f5f2" } },
  dark: { selected: { border: "#e8b34d", bg: "rgba(232,179,77,0.15)" }, unselected: { border: "#2a3733", bg: "#1b2521" } },
};

export function HomeScreen() {
  const { colorScheme } = useColorScheme();
  const pickerColors = colorScheme === "dark" ? PICKER_COLORS.dark : PICKER_COLORS.light;
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

  // Single-league assumption for now: a logged-in user's one league's roster can be
  // assigned into the wheel's Player 1/2 slots before spinning — see project notes. Once
  // the spin settles, that result is captured into `pendingMatch` (a frozen snapshot) and
  // the wheel + player pickers reset immediately, ready for the next pair.
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

  // useFocusEffect (not a plain useEffect) because bottom-tab screens stay mounted when
  // you switch away — a plain effect keyed on `isGuest` only re-runs on login/logout, so
  // a league created on the Profil tab would never appear here until the app remounted.
  // Refetching every time the Çark tab regains focus keeps it in sync with what Profil did.
  useFocusEffect(
    useCallback(() => {
      if (isGuest) return;
      let cancelled = false;
      leaguesApi
        .listMyLeagues()
        .then(async (leagues) => {
          if (cancelled) return;
          const mine = leagues[0] ?? null;
          setLeague(mine);
          setLeaguePlayers(mine ? await playersApi.listPlayers({ leagueId: mine.id! }) : []);
        })
        .catch((err) => logWarn("home.leagueLoadFailed", { error: String(err) }));
      return () => {
        cancelled = true;
      };
    }, [isGuest]),
  );

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
    setSelectedStars((current) => (current.includes(star) ? current.filter((s) => s !== star) : [...current, star]));
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
    if (spinToken.current !== token) return;

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

  async function handleRegisterMatch() {
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
    <ScrollView className="flex-1 bg-bg dark:bg-bg-dark" contentContainerClassName="px-4 py-6">
      <Text className="mb-1 text-[11px] font-bold uppercase tracking-wide text-accent dark:text-accent-dark">Maç Çekilişi</Text>
      <View className="mb-4 flex-row items-baseline justify-between">
        <Text className="text-2xl font-bold text-text dark:text-text-dark">Çark Arenası</Text>
        <Text className="text-sm text-text-faint dark:text-text-faint-dark">
          {loading ? "Yükleniyor…" : `${filteredPool.length} takım`}
        </Text>
      </View>

      {loadError && (
        <View className="mb-4 rounded-xl border border-loss/30 bg-loss/10 px-4 py-3">
          <Text className="text-sm font-semibold text-loss dark:text-loss-dark">{loadError}</Text>
        </View>
      )}

      <View className="rounded-2xl border border-border-soft bg-surface p-4 dark:border-border-soft-dark dark:bg-surface-dark">
        <View className="mb-4 flex-row flex-wrap items-center gap-2 border-b border-dashed border-border pb-4 dark:border-border-dark">
          <Text className="mr-1 text-[11px] font-bold uppercase tracking-wide text-text-faint dark:text-text-faint-dark">Yıldız</Text>
          {STAR_LEVELS.map((star) => {
            const palette = colorScheme === "dark" ? STAR_CHIP_COLORS.dark : STAR_CHIP_COLORS.light;
            const chipColors = selectedStars.includes(star) ? palette.selected : palette.unselected;
            return (
              <Pressable
                key={star}
                onPress={() => toggleStar(star)}
                className="rounded-lg border px-2.5 py-1.5"
                style={{ borderColor: chipColors.border, backgroundColor: chipColors.bg }}
              >
                <Text className="text-xs font-bold text-text dark:text-text-dark">
                  {star}
                  <Text className="text-gold dark:text-gold-dark"> ★</Text>
                </Text>
              </Pressable>
            );
          })}
        </View>

        <View className="mb-4 flex-row items-center justify-between border-b border-dashed border-border pb-4 dark:border-border-dark">
          <View className="flex-1 flex-row items-center gap-2">
            <Text className="text-[11px] font-bold uppercase tracking-wide text-text-faint dark:text-text-faint-dark">Kategori</Text>
            <View className="flex-1 overflow-hidden rounded-full border border-border bg-surface-2 dark:border-border-dark dark:bg-surface-2-dark">
              <Picker
                selectedValue={selectedLeagueTypeId ?? "all"}
                onValueChange={(value) => setSelectedLeagueTypeId(value === "all" ? undefined : Number(value))}
                style={{ height: 40, backgroundColor: pickerColors.bg, color: pickerColors.text }}
                dropdownIconColor={pickerColors.text}
              >
                <Picker.Item label="Tüm Ligler" value="all" color={pickerColors.text} />
                {leagueTypes.map((lt) => (
                  <Picker.Item key={lt.id} label={lt.name} value={lt.id} color={pickerColors.text} />
                ))}
              </Picker>
            </View>
          </View>
        </View>

        <View className="mb-4 flex-row items-center justify-between">
          <Text className="text-xs font-semibold text-text-dim dark:text-text-dim-dark">Aynı Takım</Text>
          <Switch value={mirrorMatch} onValueChange={setMirrorMatch} accessibilityRole="switch" accessibilityLabel="Aynı Takım" />
        </View>

        {canAssignPlayers && (
          <View className="mb-4 border-b border-dashed border-border pb-4 dark:border-border-dark">
            <Text className="mb-2 text-[11px] font-bold uppercase tracking-wide text-text-faint dark:text-text-faint-dark">
              Ligden oyuncu seç
            </Text>
            <View className="flex-row items-center gap-2">
              <View className="flex-1 overflow-hidden rounded-full border border-border bg-surface-2 dark:border-border-dark dark:bg-surface-2-dark">
                <Picker
                  selectedValue={selectedPlayer1Id}
                  onValueChange={(value) => setSelectedPlayer1Id(value === "" ? "" : Number(value))}
                  style={{ height: 40, backgroundColor: pickerColors.bg, color: pickerColors.text }}
                  dropdownIconColor={pickerColors.text}
                >
                  <Picker.Item label="Oyuncu 1…" value="" color={pickerColors.text} />
                  {leaguePlayers
                    .filter((p) => p.id !== selectedPlayer2Id)
                    .map((p) => (
                      <Picker.Item key={p.id} label={p.displayName} value={p.id} color={pickerColors.text} />
                    ))}
                </Picker>
              </View>
              <Text className="text-xs text-text-faint dark:text-text-faint-dark">vs</Text>
              <View className="flex-1 overflow-hidden rounded-full border border-border bg-surface-2 dark:border-border-dark dark:bg-surface-2-dark">
                <Picker
                  selectedValue={selectedPlayer2Id}
                  onValueChange={(value) => setSelectedPlayer2Id(value === "" ? "" : Number(value))}
                  style={{ height: 40, backgroundColor: pickerColors.bg, color: pickerColors.text }}
                  dropdownIconColor={pickerColors.text}
                >
                  <Picker.Item label="Oyuncu 2…" value="" color={pickerColors.text} />
                  {leaguePlayers
                    .filter((p) => p.id !== selectedPlayer1Id)
                    .map((p) => (
                      <Picker.Item key={p.id} label={p.displayName} value={p.id} color={pickerColors.text} />
                    ))}
                </Picker>
              </View>
            </View>
          </View>
        )}

        <View className="items-center gap-4">
          <PlayerPanel label={selectedPlayer1?.displayName ?? "Oyuncu 1"} team={player1} spinning={spinningPlayer === 1} />

          <Pressable
            onPress={handleSpin}
            disabled={spinning || loading || teams.length === 0 || missingPlayerSelection || pendingMatch !== null}
            className="flex-row items-center gap-2 rounded-full bg-accent px-5 py-3 dark:bg-accent-dark"
            style={{ opacity: spinning || loading || teams.length === 0 || missingPlayerSelection || pendingMatch !== null ? 0.5 : 1 }}
          >
            {spinning ? <ActivityIndicator size="small" color="#ffffff" /> : null}
            <Text className="text-sm font-extrabold text-accent-ink dark:text-accent-ink-dark">
              {spinningPlayer === 1 ? "Oyuncu 1 için çevriliyor…" : spinningPlayer === 2 ? "Oyuncu 2 için çevriliyor…" : "Çarkı Çevir"}
            </Text>
          </Pressable>

          <PlayerPanel label={selectedPlayer2?.displayName ?? "Oyuncu 2"} team={player2} spinning={spinningPlayer === 2} />
        </View>

        {spinError && <Text className="mt-4 text-center text-sm font-semibold text-loss dark:text-loss-dark">{spinError}</Text>}
      </View>

      {pendingMatch && (
        <View className="mt-5 rounded-2xl border border-border-soft bg-surface p-5 dark:border-border-soft-dark dark:bg-surface-dark">
          <Text className="mb-1 text-[11px] font-bold uppercase tracking-wide text-accent dark:text-accent-dark">Maç Kaydı</Text>
          <Text className="mb-4 text-base font-bold text-text dark:text-text-dark">
            {pendingMatch.player1Name} ({pendingMatch.team1.name}) vs {pendingMatch.player2Name} ({pendingMatch.team2.name})
          </Text>
          <View className="flex-row items-center justify-center gap-4">
            <TextInput
              value={score1}
              onChangeText={setScore1}
              keyboardType="number-pad"
              className="w-16 rounded-lg border border-border bg-surface-2 px-3 py-2 text-center text-lg font-bold text-text dark:border-border-dark dark:bg-surface-2-dark dark:text-text-dark"
            />
            <Text className="text-text-faint dark:text-text-faint-dark">-</Text>
            <TextInput
              value={score2}
              onChangeText={setScore2}
              keyboardType="number-pad"
              className="w-16 rounded-lg border border-border bg-surface-2 px-3 py-2 text-center text-lg font-bold text-text dark:border-border-dark dark:bg-surface-2-dark dark:text-text-dark"
            />
          </View>
          {registerError && <Text className="mt-3 text-center text-sm font-semibold text-loss dark:text-loss-dark">{registerError}</Text>}
          <View className="mt-4 flex-row items-center justify-center gap-2">
            <Pressable
              onPress={handleDiscardMatch}
              disabled={registering}
              className="items-center rounded-full border border-loss px-5 py-2.5 dark:border-loss-dark"
              style={{ opacity: registering ? 0.5 : 1 }}
            >
              <Text className="text-sm font-bold text-loss dark:text-loss-dark">Eşleşmeyi Sil</Text>
            </Pressable>
            <Pressable
              onPress={handleRegisterMatch}
              disabled={registering}
              className="items-center rounded-full bg-accent px-5 py-2.5 dark:bg-accent-dark"
              style={{ opacity: registering ? 0.5 : 1 }}
            >
              <Text className="text-sm font-bold text-accent-ink dark:text-accent-ink-dark">
                {registering ? "Kaydediliyor…" : "Maçı Kaydet"}
              </Text>
            </Pressable>
          </View>
        </View>
      )}

      {registered && (
        <Text className="mt-4 text-center text-sm font-semibold text-win dark:text-win-dark">
          ✓ Maç kaydedildi. Bir sonraki maç için tekrar çevirebilirsin.
        </Text>
      )}

      {!isGuest && !league && (
        <Text className="mt-6 text-center text-xs text-text-faint dark:text-text-faint-dark">
          Maçları kaydedip puan durumu tutmak için profilinden bir lig oluştur.
        </Text>
      )}
      {isGuest && (
        <Text className="mt-6 text-center text-xs text-text-faint dark:text-text-faint-dark">
          Giriş yapıp bir lig oluşturursan çektiğin maçları kaydedip puan durumu tutabilirsin.
        </Text>
      )}
    </ScrollView>
  );
}

function PlayerPanel({ label, team, spinning }: { label: string; team: WheelTeam | null; spinning: boolean }) {
  return (
    <View className="w-full items-center gap-3 rounded-2xl border border-border bg-surface-2 p-5 dark:border-border-dark dark:bg-surface-2-dark">
      <Text className="text-[10px] font-extrabold uppercase tracking-wide text-text-faint dark:text-text-faint-dark">{label}</Text>
      <TeamCrest code={team?.code} colorHex={team?.colorHex} spinning={spinning} />
      <View className="items-center">
        <Text className="text-base font-bold text-text dark:text-text-dark">{team?.name ?? "—"}</Text>
        {team && <Text className="mt-1 text-xs text-gold dark:text-gold-dark">{"★".repeat(team.starLevel)}</Text>}
      </View>
    </View>
  );
}
