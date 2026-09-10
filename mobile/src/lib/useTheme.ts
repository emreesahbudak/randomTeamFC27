import { useCallback, useEffect } from "react";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { useColorScheme } from "nativewind";

export type Theme = "light" | "dark";

const STORAGE_KEY = "fc27-theme";

/** Manual light/dark toggle, persisted to AsyncStorage, applied via NativeWind's
 *  `colorScheme` (darkMode: "class" in tailwind.config.js) — mirrors web's useTheme.ts,
 *  which also ignores prefers-color-scheme once the user has picked explicitly. */
export function useTheme(): [Theme, () => void] {
  const { colorScheme, setColorScheme } = useColorScheme();

  useEffect(() => {
    AsyncStorage.getItem(STORAGE_KEY).then((stored) => {
      if (stored === "light" || stored === "dark") {
        setColorScheme(stored);
      }
    });
    // Runs once on mount to hydrate the persisted choice.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const toggle = useCallback(() => {
    const next: Theme = colorScheme === "dark" ? "light" : "dark";
    setColorScheme(next);
    AsyncStorage.setItem(STORAGE_KEY, next).catch(() => {
      // Ignore — theme just won't persist across app restarts in this environment.
    });
  }, [colorScheme, setColorScheme]);

  return [colorScheme === "dark" ? "dark" : "light", toggle];
}
