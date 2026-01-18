import { createBrowserRouter, Navigate } from "react-router-dom";
import { LandingPage } from "./pages/LandingPage";
import { AuthPage } from "./pages/AuthPage";
import { DecidePage } from "./pages/DecidePage";
import { ItemsPage } from "./pages/ItemsPage";
import { HistoryPage } from "./pages/HistoryPage";
import { HistoryDetailPage } from "./pages/HistoryDetailPage";
import { PreferencesPage } from "./pages/PreferencesPage";
import { SettingsPage } from "./pages/SettingsPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { DevConsole } from "../dev/DevConsole";
import { RequireAuth } from "./auth/RequireAuth";
import { DecideGate } from "./auth/DecideGate";

export const router = createBrowserRouter([
  {
    path: "/",
    element: <LandingPage />,
  },
  {
    path: "/auth",
    element: <AuthPage />,
  },
  {
    path: "/decide",
    element: (
      <RequireAuth>
        <DecideGate>
          <DecidePage />
        </DecideGate>
      </RequireAuth>
    ),
  },
  {
    path: "/items",
    element: (
      <RequireAuth>
        <ItemsPage />
      </RequireAuth>
    ),
  },
  {
    path: "/history",
    element: (
      <RequireAuth>
        <HistoryPage />
      </RequireAuth>
    ),
  },
  {
    path: "/history/:id",
    element: (
      <RequireAuth>
        <HistoryDetailPage />
      </RequireAuth>
    ),
  },
  {
    path: "/preferences",
    element: (
      <RequireAuth>
        <PreferencesPage />
      </RequireAuth>
    ),
  },
  {
    path: "/settings",
    element: <SettingsPage />,
  },
  {
    path: "/dev",
    element: <DevConsole />,
  },
  {
    path: "/app",
    element: <Navigate to="/" replace />,
  },
  {
    path: "*",
    element: <NotFoundPage />,
  },
]);
