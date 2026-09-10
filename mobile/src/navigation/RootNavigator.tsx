import { NavigationContainer } from "@react-navigation/native";
import { createBottomTabNavigator } from "@react-navigation/bottom-tabs";
import { Ionicons } from "@expo/vector-icons";
import { useColorScheme } from "nativewind";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { HomeScreen } from "../screens/HomeScreen";
import { ProfileScreen } from "../screens/ProfileScreen";
import { LeagueScreen } from "../screens/LeagueScreen";
import { Header } from "../components/Header";

const Tab = createBottomTabNavigator();

const COLORS = {
  light: { bg: "#eef2f0", border: "#d7ded9", text: "#0f1713", faint: "#8a978f", accent: "#0f9a82" },
  dark: { bg: "#0a0f0d", border: "#2a3733", text: "#eaf2ed", faint: "#5e7268", accent: "#2fe6c6" },
};

export function RootNavigator() {
  const { colorScheme } = useColorScheme();
  const palette = colorScheme === "dark" ? COLORS.dark : COLORS.light;
  // Without this, the tab bar's icons/labels sit flush against the very bottom edge —
  // on a real device that's the home-indicator/gesture-bar area, so they look clipped.
  const insets = useSafeAreaInsets();

  return (
    <NavigationContainer>
      <Tab.Navigator
        screenOptions={{
          header: () => <Header />,
          tabBarActiveTintColor: palette.accent,
          tabBarInactiveTintColor: palette.faint,
          tabBarStyle: {
            backgroundColor: palette.bg,
            borderTopColor: palette.border,
            height: 52 + insets.bottom,
            paddingBottom: insets.bottom + 6,
            paddingTop: 6,
          },
        }}
      >
        <Tab.Screen
          name="Çark"
          component={HomeScreen}
          options={{ tabBarIcon: ({ color, size }) => <Ionicons name="radio-button-on-outline" color={color} size={size} /> }}
        />
        <Tab.Screen
          name="Lig"
          component={LeagueScreen}
          options={{ tabBarIcon: ({ color, size }) => <Ionicons name="trophy-outline" color={color} size={size} /> }}
        />
        <Tab.Screen
          name="Profil"
          component={ProfileScreen}
          options={{ tabBarIcon: ({ color, size }) => <Ionicons name="person-outline" color={color} size={size} /> }}
        />
      </Tab.Navigator>
    </NavigationContainer>
  );
}
