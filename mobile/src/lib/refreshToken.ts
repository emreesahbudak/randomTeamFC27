import { Platform } from "react-native";
import * as SecureStore from "expo-secure-store";
import AsyncStorage from "@react-native-async-storage/async-storage";

const KEY = "fc27-refresh-token";

/**
 * Mobile has no cookies, so the refresh token (a long-lived credential) is kept in
 * SecureStore (hardware-encrypted) rather than in the Zustand-persisted auth store,
 * which only uses AsyncStorage (unencrypted) — mirrors the reasoning already documented
 * in shared/src/authStore.ts for why accessToken/refreshToken are excluded from persist().
 *
 * expo-secure-store ships no web implementation at all (its web module is a literal `{}`
 * stub — every method throws "is not a function"), so `Platform.OS === "web"` falls back
 * to AsyncStorage instead. This only matters for `expo start --web`, used here as a
 * browser-testable stand-in since no iOS/Android simulator is set up on this machine —
 * real device/native builds always go through the encrypted SecureStore path below.
 */
const isWeb = Platform.OS === "web";

export async function getRefreshToken(): Promise<string | null> {
  return isWeb ? AsyncStorage.getItem(KEY) : SecureStore.getItemAsync(KEY);
}

export async function setRefreshToken(token: string): Promise<void> {
  await (isWeb ? AsyncStorage.setItem(KEY, token) : SecureStore.setItemAsync(KEY, token));
}

export async function clearRefreshToken(): Promise<void> {
  await (isWeb ? AsyncStorage.removeItem(KEY) : SecureStore.deleteItemAsync(KEY));
}
