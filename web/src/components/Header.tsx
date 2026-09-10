import { useState, useRef, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuthStore, authApi } from "../lib/auth";
import { useTheme } from "../lib/useTheme";
import { logInfo } from "@fc27/shared";

export function Header() {
  const [theme, toggleTheme] = useTheme();
  const { user, isGuest, logout } = useAuthStore();
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  useEffect(() => {
    if (!menuOpen) return;
    function onClickOutside(event: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setMenuOpen(false);
      }
    }
    document.addEventListener("mousedown", onClickOutside);
    return () => document.removeEventListener("mousedown", onClickOutside);
  }, [menuOpen]);

  async function handleLogout() {
    setMenuOpen(false);
    try {
      await authApi.logout();
    } catch (error) {
      logInfo("header.logoutRequestFailed", { error: String(error) });
    }
    logout();
    navigate("/login");
  }

  const initial = user?.displayName?.trim()?.[0]?.toUpperCase() ?? "G";

  return (
    <header className="sticky top-0 z-20 flex items-center justify-between gap-3 border-b border-border-soft bg-bg/85 px-4 py-3 backdrop-blur sm:px-6">
      <Link to="/" className="font-display text-lg font-bold text-text hover:text-accent">
        FC27 Takım ve Lig Yöneticisi
      </Link>

      <div className="flex items-center gap-2.5">
        <button
          type="button"
          onClick={toggleTheme}
          title="Temayı değiştir"
          aria-label="Temayı değiştir"
          className="flex h-9 w-9 items-center justify-center rounded-lg border border-border bg-surface text-text-dim hover:text-text"
        >
          {theme === "dark" ? "◐" : "◑"}
        </button>

        <div className="relative" ref={menuRef}>
          <button
            type="button"
            onClick={() => setMenuOpen((open) => !open)}
            title={isGuest ? "Giriş yap" : "Hesap menüsü"}
            aria-label={isGuest ? "Giriş yap" : "Hesap menüsü"}
            className={
              isGuest
                ? "flex h-9 w-9 items-center justify-center rounded-full border-2 border-dashed border-border bg-surface-2 font-display text-sm font-bold text-text-dim"
                : "flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-br from-accent to-accent-dim font-display text-sm font-bold text-accent-ink"
            }
          >
            {initial}
          </button>

          {menuOpen && (
            <div className="absolute right-0 mt-2 w-56 rounded-xl border border-border-soft bg-surface p-1.5 shadow-lg">
              {isGuest ? (
                <Link
                  to="/login"
                  onClick={() => setMenuOpen(false)}
                  className="block rounded-lg px-3 py-2 text-left text-sm font-semibold text-text hover:bg-surface-2"
                >
                  Giriş yap
                </Link>
              ) : (
                <>
                  <div className="px-3 py-2">
                    <div className="text-sm font-bold text-text">{user?.displayName}</div>
                    <div className="text-xs text-text-faint">{user?.email}</div>
                  </div>
                  <Link
                    to="/profile"
                    onClick={() => setMenuOpen(false)}
                    className="block rounded-lg px-3 py-2 text-left text-sm font-semibold text-text hover:bg-surface-2"
                  >
                    Profil ve Lig Ayarları
                  </Link>
                  <Link
                    to="/league"
                    onClick={() => setMenuOpen(false)}
                    className="block rounded-lg px-3 py-2 text-left text-sm font-semibold text-text hover:bg-surface-2"
                  >
                    Puan Durumu
                  </Link>
                  {user?.role === "ADMIN" && (
                    <Link
                      to="/admin/teams"
                      onClick={() => setMenuOpen(false)}
                      className="block rounded-lg px-3 py-2 text-left text-sm font-semibold text-text hover:bg-surface-2"
                    >
                      Yönetici: Takımlar
                    </Link>
                  )}
                  <button
                    type="button"
                    onClick={handleLogout}
                    className="block w-full rounded-lg px-3 py-2 text-left text-sm font-semibold text-loss hover:bg-surface-2"
                  >
                    Çıkış yap
                  </button>
                </>
              )}
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
