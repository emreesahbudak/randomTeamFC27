import { logDebug, logWarn } from "./logger";

/**
 * The subset of a team's fields the wheel actually needs. Deliberately not the generated
 * `TeamResponse` type (whose fields are all optional per the OpenAPI schema) — this keeps
 * the algorithm's types honest (id/starLevel/leagueTypeId are always required) and decouples
 * it from API DTO shape churn. Any real `TeamResponse` from the backend satisfies this shape.
 */
export interface WheelTeam {
  id: number;
  name: string;
  code: string;
  starLevel: number;
  leagueTypeId: number;
  crestUrl?: string | null;
  colorHex?: string | null;
}

export interface WheelFilters {
  /** Only include teams whose starLevel is one of these. Empty/undefined = no star filter. */
  starLevels?: number[];
  /** Only include teams in this league type. undefined = no league filter. */
  leagueTypeId?: number;
}

export class WheelError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "WheelError";
  }
}

/**
 * Pure filter — the same star+league filtering rules the backend's `GET /api/teams` query
 * params apply, reimplemented here so web/mobile can filter an already-fetched pool locally
 * (instant wheel re-spin on filter change, no extra round-trip) without the two platforms
 * ever risking diverging filter semantics.
 */
export function filterTeams<T extends WheelTeam>(teams: T[], filters: WheelFilters): T[] {
  const starLevels = filters.starLevels;
  const hasStarFilter = !!starLevels && starLevels.length > 0;

  return teams.filter((team) => {
    if (hasStarFilter && !starLevels!.includes(team.starLevel)) {
      return false;
    }
    if (filters.leagueTypeId != null && team.leagueTypeId !== filters.leagueTypeId) {
      return false;
    }
    return true;
  });
}

export interface SpinOptions {
  /** When false (default), Player 1 and Player 2 can never land on the same team. */
  mirrorMatch?: boolean;
  /** Injectable RNG (must return a float in [0, 1)) — lets tests be fully deterministic. */
  random?: () => number;
}

export interface SpinResult<T extends WheelTeam> {
  player1Team: T;
  player2Team: T;
}

/**
 * Picks one team uniformly at random for each player from an already-filtered pool. Every
 * team has an equal chance — that's the whole point of the app ("adil takım seçimi") — so
 * this stays a plain uniform array-index pick, no weighting.
 */
export function spinWheel<T extends WheelTeam>(pool: T[], options: SpinOptions = {}): SpinResult<T> {
  const random = options.random ?? Math.random;
  const mirrorMatch = options.mirrorMatch ?? false;

  if (pool.length === 0) {
    throw new WheelError("Filtrelere uyan takım bulunamadı");
  }

  const player1Team = pickRandom(pool, random);

  let player2Candidates = pool;
  if (!mirrorMatch) {
    player2Candidates = pool.filter((team) => team.id !== player1Team.id);
    if (player2Candidates.length === 0) {
      throw new WheelError(
        "Filtrelere uyan tek takım var — en az iki takım gerekiyor, ya da Aynı Takım seçeneğini aç",
      );
    }
  }
  const player2Team = pickRandom(player2Candidates, random);

  logDebug("wheel.spin", {
    poolSize: pool.length,
    mirrorMatch,
    player1TeamId: player1Team.id,
    player2TeamId: player2Team.id,
  });

  return { player1Team, player2Team };
}

/**
 * Filters then spins in one call — the common case for the wheel UI's "Spin" button.
 */
export function filterAndSpin<T extends WheelTeam>(
  teams: T[],
  filters: WheelFilters,
  options: SpinOptions = {},
): SpinResult<T> {
  const pool = filterTeams(teams, filters);
  if (pool.length === 0) {
    logWarn("wheel.emptyPool", { totalTeams: teams.length, filters });
  }
  return spinWheel(pool, options);
}

function pickRandom<T>(items: T[], random: () => number): T {
  const index = Math.floor(random() * items.length);
  // Guard against a pathological random() returning exactly 1 (out of spec, but cheap to guard).
  return items[Math.min(index, items.length - 1)];
}
