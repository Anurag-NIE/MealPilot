import { create } from "zustand";
import { persist } from "zustand/middleware";

function defaultApiBase(): string {
  const envBase = (import.meta as any).env?.VITE_API_BASE as string | undefined;
  if (envBase && envBase.trim()) return envBase;

  // Dev default: Vite on 5173, API on 9000.
  if (typeof window !== "undefined" && window.location?.port === "5173") {
    return "http://localhost:9000";
  }

  // Container/prod default: same origin (nginx proxies /api to the backend).
  if (typeof window !== "undefined" && window.location?.origin) {
    return window.location.origin;
  }

  return "http://localhost:9000";
}

function decodeJwtPayload(token: string): any | null {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const json = decodeURIComponent(
      atob(payload)
        .split("")
        .map((c) => "%" + c.charCodeAt(0).toString(16).padStart(2, "0"))
        .join(""),
    );
    return JSON.parse(json);
  } catch {
    return null;
  }
}

function tokenExpiryMs(token: string): number | null {
  const payload = decodeJwtPayload(token);
  const exp = payload?.exp;
  if (typeof exp !== "number") return null;
  return exp * 1000;
}

function sanitizeToken(input: string): string {
  const t = (input ?? "").trim();
  if (!t) return "";
  // Common paste case: users paste the whole header value.
  if (/^bearer\s+/i.test(t)) return t.replace(/^bearer\s+/i, "").trim();
  return t;
}

export type SessionState = {
  apiBase: string;
  token: string;
  refreshToken: string;
  enableRefresh: boolean;

  setApiBase: (next: string) => void;
  setToken: (next: string) => void;
  setRefreshToken: (next: string) => void;
  setEnableRefresh: (next: boolean) => void;
  logout: () => void;

  hasToken: boolean;
  tokenExpiresAtMs: number | null;
  isTokenExpired: boolean;

  ensureDefaults: () => void;
};

export const useSessionStore = create<SessionState>()(
  persist(
    (set, get) => ({
      apiBase: defaultApiBase(),
      token: "",
      refreshToken: "",
      enableRefresh: false,

      hasToken: false,
      tokenExpiresAtMs: null,
      isTokenExpired: false,

      setApiBase: (next) => set({ apiBase: next }),
      setToken: (next) => {
        const token = sanitizeToken(next);
        const expMs = token ? tokenExpiryMs(token) : null;
        set({
          token,
          hasToken: Boolean(token),
          tokenExpiresAtMs: expMs,
          isTokenExpired: expMs ? Date.now() >= expMs : false,
        });
      },
      setRefreshToken: (next) => set({ refreshToken: next }),
      setEnableRefresh: (next) => set({ enableRefresh: next }),

      logout: () =>
        set({
          token: "",
          refreshToken: "",
          hasToken: false,
          tokenExpiresAtMs: null,
          isTokenExpired: false,
        }),

      ensureDefaults: () => {
        const cur = get().apiBase;
        if (!cur) set({ apiBase: defaultApiBase() });
      },
    }),
    {
      name: "mealpilot.session",
      partialize: (s) => ({
        apiBase: s.apiBase,
        token: s.token,
        refreshToken: s.refreshToken,
        enableRefresh: s.enableRefresh,
      }),
      onRehydrateStorage: () => (state) => {
        if (!state) return;
        // Persist only stores raw token; recompute derived auth flags post-hydration.
        state.setToken(state.token);
      },
    },
  ),
);

export function getSessionSnapshot() {
  return useSessionStore.getState();
}
