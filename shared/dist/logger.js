/**
 * Structured logging for the shared layer (wheel algorithm, auth store, API requests) — the
 * frontend counterpart to the backend's Slf4j logging. Every entry is a single JSON-shaped
 * object rather than an interpolated string, so web/mobile console output (and any future
 * log-shipping) stays consistently parseable across both platforms.
 */
const consoleSink = (entry) => {
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
let activeSink = consoleSink;
/** Swap the sink (e.g. in tests, to a spy) — defaults to structured console output. */
export function setLogSink(sink) {
    activeSink = sink;
}
export function resetLogSink() {
    activeSink = consoleSink;
}
function log(level, event, data) {
    activeSink({ timestamp: new Date().toISOString(), level, event, ...data });
}
export const logDebug = (event, data) => log("debug", event, data);
export const logInfo = (event, data) => log("info", event, data);
export const logWarn = (event, data) => log("warn", event, data);
export const logError = (event, data) => log("error", event, data);
