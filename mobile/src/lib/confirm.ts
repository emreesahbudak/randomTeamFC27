import { Platform, Alert } from "react-native";

/**
 * `Alert.alert()` is a complete no-op on `react-native-web` (its web module is literally
 * `static alert() {}` — see node_modules/react-native-web/src/exports/Alert), so a
 * destructive-confirm flow built on it silently does nothing under `expo start --web`.
 * Real device builds don't need this — `Alert.alert` works normally there — but since this
 * project's only browser-based verification path is the web target, every confirm dialog
 * needs a web-safe branch too. `window.confirm`/`window.alert` are real browser natives
 * (same ones web/'s own confirm()/alert() calls already rely on), so branching on
 * `Platform.OS === "web"` costs nothing and fixes both device and browser.
 */
export function confirmAsync(title: string, message: string, confirmLabel: string): Promise<boolean> {
  if (Platform.OS === "web") {
    return Promise.resolve(window.confirm(`${title}\n\n${message}`));
  }
  return new Promise((resolve) => {
    Alert.alert(title, message, [
      { text: "Vazgeç", style: "cancel", onPress: () => resolve(false) },
      { text: confirmLabel, style: "destructive", onPress: () => resolve(true) },
    ]);
  });
}

export function notify(title: string, message?: string): void {
  if (Platform.OS === "web") {
    window.alert(message ? `${title}\n\n${message}` : title);
  } else {
    Alert.alert(title, message);
  }
}
