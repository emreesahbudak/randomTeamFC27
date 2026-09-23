import { logDebug, logWarn } from "./logger";
export class WheelError extends Error {
    constructor(message) {
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
export function filterTeams(teams, filters) {
    const starLevels = filters.starLevels;
    const hasStarFilter = !!starLevels && starLevels.length > 0;
    return teams.filter((team) => {
        if (hasStarFilter && !starLevels.includes(team.starLevel)) {
            return false;
        }
        if (filters.leagueTypeId != null && team.leagueTypeId !== filters.leagueTypeId) {
            return false;
        }
        return true;
    });
}
/**
 * Picks one team uniformly at random for each player from an already-filtered pool. Every
 * team has an equal chance — that's the whole point of the app ("adil takım seçimi") — so
 * this stays a plain uniform array-index pick, no weighting.
 */
export function spinWheel(pool, options = {}) {
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
            throw new WheelError("Filtrelere uyan tek takım var — en az iki takım gerekiyor, ya da Aynı Takım seçeneğini aç");
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
export function filterAndSpin(teams, filters, options = {}) {
    const pool = filterTeams(teams, filters);
    if (pool.length === 0) {
        logWarn("wheel.emptyPool", { totalTeams: teams.length, filters });
    }
    return spinWheel(pool, options);
}
function pickRandom(items, random) {
    const index = Math.floor(random() * items.length);
    // Guard against a pathological random() returning exactly 1 (out of spec, but cheap to guard).
    return items[Math.min(index, items.length - 1)];
}
