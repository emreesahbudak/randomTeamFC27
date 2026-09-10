import { logDebug, logInfo, logWarn, logError, setLogSink, resetLogSink, type LogEntry } from "./logger";

describe("logger", () => {
  let entries: LogEntry[];

  beforeEach(() => {
    entries = [];
    setLogSink((entry) => entries.push(entry));
  });

  afterEach(() => {
    resetLogSink();
  });

  it("captures level, event, timestamp, and extra data", () => {
    logInfo("auth.login", { userId: 42 });

    expect(entries).toHaveLength(1);
    expect(entries[0].level).toBe("info");
    expect(entries[0].event).toBe("auth.login");
    expect(entries[0].userId).toBe(42);
    expect(typeof entries[0].timestamp).toBe("string");
    expect(() => new Date(entries[0].timestamp)).not.toThrow();
  });

  it("supports all four levels", () => {
    logDebug("d");
    logInfo("i");
    logWarn("w");
    logError("e");

    expect(entries.map((e) => e.level)).toEqual(["debug", "info", "warn", "error"]);
  });

  it("works without extra data", () => {
    logWarn("wheel.emptyPool");

    expect(entries[0]).toMatchObject({ level: "warn", event: "wheel.emptyPool" });
  });
});
