import React from "react";
import { useAuthExpiry } from "./auth/useAuthExpiry";
import { useSessionStore } from "./store/sessionStore";

export function AppBootstrap(props: { children: React.ReactNode }) {
  const ensureDefaults = useSessionStore((s) => s.ensureDefaults);

  React.useEffect(() => {
    ensureDefaults();
  }, [ensureDefaults]);

  useAuthExpiry();

  return <>{props.children}</>;
}
