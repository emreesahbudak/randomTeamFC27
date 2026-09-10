import { filterTeams, spinWheel, filterAndSpin, WheelError, type WheelTeam } from "./wheel";

function team(overrides: Partial<WheelTeam> & { id: number }): WheelTeam {
  return {
    name: `Team ${overrides.id}`,
    code: `T${overrides.id}`,
    starLevel: 3,
    leagueTypeId: 1,
    ...overrides,
  };
}

describe("filterTeams", () => {
  const pool = [
    team({ id: 1, starLevel: 5, leagueTypeId: 1 }),
    team({ id: 2, starLevel: 2, leagueTypeId: 1 }),
    team({ id: 3, starLevel: 5, leagueTypeId: 2 }),
  ];

  it("returns everything when no filters are given", () => {
    expect(filterTeams(pool, {})).toHaveLength(3);
  });

  it("filters by star level", () => {
    const result = filterTeams(pool, { starLevels: [5] });
    expect(result.map((t) => t.id)).toEqual([1, 3]);
  });

  it("filters by multiple star levels", () => {
    const result = filterTeams(pool, { starLevels: [2, 5] });
    expect(result).toHaveLength(3);
  });

  it("filters by league type id", () => {
    const result = filterTeams(pool, { leagueTypeId: 2 });
    expect(result.map((t) => t.id)).toEqual([3]);
  });

  it("combines star and league filters", () => {
    const result = filterTeams(pool, { starLevels: [5], leagueTypeId: 1 });
    expect(result.map((t) => t.id)).toEqual([1]);
  });

  it("returns an empty array when nothing matches", () => {
    expect(filterTeams(pool, { starLevels: [4] })).toEqual([]);
  });

  it("treats an empty starLevels array as no filter (not 'match nothing')", () => {
    expect(filterTeams(pool, { starLevels: [] })).toHaveLength(3);
  });
});

describe("spinWheel", () => {
  const pool = [team({ id: 1 }), team({ id: 2 }), team({ id: 3 })];

  it("throws WheelError on an empty pool", () => {
    expect(() => spinWheel([])).toThrow(WheelError);
  });

  it("picks player1 and player2 from the pool", () => {
    const result = spinWheel(pool, { random: () => 0 });
    expect(pool).toContainEqual(result.player1Team);
    expect(pool).toContainEqual(result.player2Team);
  });

  it("uses the injected RNG deterministically", () => {
    // random() = 0 -> index 0; a second call needed for player2 since mirrorMatch is off
    // and player1 (id 1) is excluded from player2's candidate pool of [2, 3].
    const result = spinWheel(pool, { random: () => 0 });
    expect(result.player1Team.id).toBe(1);
    expect(result.player2Team.id).toBe(2);
  });

  it("never picks the same team twice when mirrorMatch is off (default)", () => {
    for (let i = 0; i < 50; i++) {
      const result = spinWheel(pool);
      expect(result.player1Team.id).not.toBe(result.player2Team.id);
    }
  });

  it("can pick the same team for both players when mirrorMatch is on", () => {
    // Force both picks to land on the same index by fixing the RNG.
    const result = spinWheel(pool, { mirrorMatch: true, random: () => 0 });
    expect(result.player1Team.id).toBe(result.player2Team.id);
  });

  it("throws a clear WheelError when only one team matches and mirrorMatch is off", () => {
    expect(() => spinWheel([team({ id: 1 })])).toThrow(WheelError);
    expect(() => spinWheel([team({ id: 1 })])).toThrow(/Aynı Takım/);
  });

  it("does not throw for a single-team pool when mirrorMatch is on", () => {
    const result = spinWheel([team({ id: 1 })], { mirrorMatch: true });
    expect(result.player1Team.id).toBe(1);
    expect(result.player2Team.id).toBe(1);
  });
});

describe("filterAndSpin", () => {
  const pool = [
    team({ id: 1, starLevel: 5 }),
    team({ id: 2, starLevel: 2 }),
    team({ id: 3, starLevel: 5 }),
  ];

  it("filters before spinning", () => {
    const result = filterAndSpin(pool, { starLevels: [5] }, { random: () => 0 });
    expect([1, 3]).toContain(result.player1Team.id);
    expect([1, 3]).toContain(result.player2Team.id);
  });

  it("throws WheelError when the filter eliminates every team", () => {
    expect(() => filterAndSpin(pool, { starLevels: [4] })).toThrow(WheelError);
  });
});
