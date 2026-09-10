/**
 * Structured logging for the shared layer (wheel algorithm, auth store, API requests) — the
 * frontend counterpart to the backend's Slf4j logging. Every entry is a single JSON-shaped
 * object rather than an interpolated string, so web/mobile console output (and any future
 * log-shipping) stays consistently parseable across both platforms.
 */

export type LogLevel = "debug" | "info" | "warn" | "error";

export interface LogEntry {
  timestamp: string;
  level: LogLevel;
  event: string;
  [key: string]: unknown;
}

export type LogSink = (entry: LogEntry) => void;

const consoleSink: LogSink = (entry) => {
  const line = JSON.stringify(entry);
  switch (entry.level) {
    case "debug":
      console.debug(line);
      break;
    case "info":
      console.info(line);
      break;
    case "warn":
      console.warn(line);
      break;
    case "error":
      console.error(line);
      break;
  }
};

let activeSink: LogSink = consoleSink;

/** Swap the sink (e.g. in tests, to a spy) — defaults to structured console output. */
export function setLogSink(sink: LogSink): void {
  activeSink = sink;
}

export function resetLogSink(): void {
  activeSink = consoleSink;
}

function log(level: LogLevel, event: string, data?: Record<string, unknown>): void {
  activeSink({ timestamp: new Date().toISOString(), level, event, ...data });
}

export const logDebug = (event: string, data?: Record<string, unknown>): void => log("debug", event, data);
export const logInfo = (event: string, data?: Record<string, unknown>): void => log("info", event, data);
export const logWarn = (event: string, data?: Record<string, unknown>): void => log("warn", event, data);
export const logError = (event: string, data?: Record<string, unknown>): void => log("error", event, data);
