import { View, Text, Pressable } from "react-native";
import { useTheme } from "../lib/useTheme";

/** Sticky-feeling top bar (rendered per-screen by RootNavigator's tab header option) —
 *  brand name + theme toggle, mirrors web/src/components/Header.tsx's chrome. Account
 *  menu/avatar isn't needed here: mobile's bottom tab bar has its own "Profil" tab. */
export function Header() {
  const [theme, toggleTheme] = useTheme();

  return (
    <View className="flex-row items-center justify-between border-b border-border-soft bg-bg px-4 py-3 dark:border-border-soft-dark dark:bg-bg-dark">
      <Text className="text-lg font-bold text-text dark:text-text-dark">FC27 Takım ve Lig Yöneticisi</Text>
      <Pressable
        onPress={toggleTheme}
        accessibilityRole="button"
        accessibilityLabel="Temayı değiştir"
        className="h-9 w-9 items-center justify-center rounded-lg border border-border bg-surface dark:border-border-dark dark:bg-surface-dark"
      >
        <Text className="text-text-dim dark:text-text-dim-dark">{theme === "dark" ? "◐" : "◑"}</Text>
      </Pressable>
    </View>
  );
}
