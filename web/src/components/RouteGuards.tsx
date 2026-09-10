import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAuthStore } from "../lib/auth";

export function RequireAuth({ children }: { children: ReactNode }) {
  const isGuest = useAuthStore((state) => state.isGuest);
  if (isGuest) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}

export function RequireAdmin({ children }: { children: ReactNode }) {
  const user = useAuthStore((state) => state.user);
  const isGuest = useAuthStore((state) => state.isGuest);
  if (isGuest) {
    return <Navigate to="/login" replace />;
  }
  if (user?.role !== "ADMIN") {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
}
