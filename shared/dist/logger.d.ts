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
/** Swap the sink (e.g. in tests, to a spy) — defaults to structured console output. */
export declare function setLogSink(sink: LogSink): void;
export declare function resetLogSink(): void;
export declare const logDebug: (event: string, data?: Record<string, unknown>) => void;
export declare const logInfo: (event: string, data?: Record<string, unknown>) => void;
export declare const logWarn: (event: string, data?: Record<string, unknown>) => void;
export declare const logError: (event: string, data?: Record<string, unknown>) => void;
