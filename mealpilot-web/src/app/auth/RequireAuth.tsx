import React from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useSessionStore } from "../store/sessionStore";

export function RequireAuth(props: { children: React.ReactNode }) {
  const location = useLocation();
  const hasToken = useSessionStore((s) => s.hasToken);
  const isExpired = useSessionStore((s) => s.isTokenExpired);

  if (!hasToken || isExpired) {
    return <Navigate to="/auth" replace state={{ from: location.pathname }} />;
  }

  return <>{props.children}</>;
}
