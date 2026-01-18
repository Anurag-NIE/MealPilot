import { useMemo } from "react";
import { createMealpilotClient } from "../gen/client";
import { useSessionStore } from "./store/sessionStore";

export function useApiClient() {
  const apiBase = useSessionStore((s) => s.apiBase);
  const token = useSessionStore((s) => s.token);

  return useMemo(() => {
    return createMealpilotClient({
      baseUrl: apiBase,
      token: token || undefined,
    });
  }, [apiBase, token]);
}
