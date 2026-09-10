import { View, Text } from "react-native";

interface TeamCrestProps {
  code?: string;
  colorHex?: string | null;
  size?: "sm" | "lg";
  spinning?: boolean;
}

/**
 * Colored badge with the team's 3-letter code. The web version clips a true hexagon via
 * CSS `clip-path` (see web/src/index.css's `.crest`); React Native's View has no clip-path
 * equivalent without pulling in react-native-svg just for this one shape, so mobile uses a
 * plain rounded square instead — same color/code content, simplified silhouette.
 */
export function TeamCrest({ code, colorHex, size = "lg", spinning = false }: TeamCrestProps) {
  const dimension = size === "lg" ? 70 : 44;
  const textClass = size === "lg" ? "text-lg" : "text-[11px]";

  if (!code) {
    return (
      <View
        style={{ width: dimension, height: dimension }}
        className="items-center justify-center rounded-2xl border border-border bg-surface-3 dark:border-border-dark dark:bg-surface-3-dark"
      >
        <Text className={`${textClass} font-bold text-text-faint dark:text-text-faint-dark`}>?</Text>
      </View>
    );
  }

  return (
    <View
      // opacity is set via `style`, not a conditionally-toggled className: NativeWind (Fabric)
      // expects a component's "special" style features (opacity/animation/variables) to be
      // fixed at initial render — flipping them by swapping classNames after mount triggers
      // its "should only happen during initial render" remount warning path, which crashed
      // outright on a real device during the wheel's rapid re-renders (every ~90ms while spinning).
      style={{ width: dimension, height: dimension, backgroundColor: colorHex ?? "#54625c", opacity: spinning ? 0.7 : 1 }}
      className="items-center justify-center rounded-2xl shadow-md"
    >
      <Text className={`${textClass} font-bold text-white`}>{code.slice(0, 3)}</Text>
    </View>
  );
}
