import { useEffect } from "react";
import { useSessionStore } from "../store/sessionStore";
import { pushToast } from "../store/toastStore";

// Logs out automatically when the JWT expires (if it contains an `exp` claim).
export function useAuthExpiry() {
  const token = useSessionStore((s) => s.token);
  const expiresAtMs = useSessionStore((s) => s.tokenExpiresAtMs);
  const logout = useSessionStore((s) => s.logout);

  useEffect(() => {
    if (!token) return;
    if (!expiresAtMs) return;

    const msLeft = expiresAtMs - Date.now();
    if (msLeft <= 0) {
      pushToast({
        kind: "warning",
        title: "Session expired",
        message: "Please log in again.",
      });
      logout();
      return;
    }

    const id = window.setTimeout(() => {
      pushToast({
        kind: "warning",
        title: "Session expired",
        message: "Please log in again.",
      });
      logout();
    }, msLeft);
    return () => window.clearTimeout(id);
  }, [token, expiresAtMs, logout]);
}
