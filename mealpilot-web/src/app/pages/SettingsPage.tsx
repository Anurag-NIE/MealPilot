import React, { useState } from "react";
import { Link } from "react-router-dom";
import { AppShell } from "../layout/AppShell";
import { useSessionStore } from "../store/sessionStore";
import { Button } from "../../ui/Button";
import { Field } from "../../ui/Field";

export function SettingsPage() {
  const apiBase = useSessionStore((s) => s.apiBase);
  const setApiBase = useSessionStore((s) => s.setApiBase);
  const logout = useSessionStore((s) => s.logout);
  const hasToken = useSessionStore((s) => s.hasToken);

  const enableRefresh = useSessionStore((s) => s.enableRefresh);
  const setEnableRefresh = useSessionStore((s) => s.setEnableRefresh);
  const refreshToken = useSessionStore((s) => s.refreshToken);
  const setRefreshToken = useSessionStore((s) => s.setRefreshToken);

  const [nextBase, setNextBase] = useState(apiBase);

  return (
    <AppShell title="Settings">
      <div className="grid2">
        <div className="card">
          <h3 className="cardTitle">Backend</h3>
          <div style={{ marginTop: 10 }}>
            <Field label="API base URL">
              <input
                value={nextBase}
                onChange={(e) => setNextBase(e.target.value)}
                placeholder="http://localhost:9000"
              />
            </Field>
          </div>
          <div className="row" style={{ marginTop: 10 }}>
            <Button variant="primary" onClick={() => setApiBase(nextBase)}>
              Save
            </Button>
            <a className="btn" href={apiBase} target="_blank" rel="noreferrer">
              Open backend landing
            </a>
          </div>
          <div className="small" style={{ marginTop: 10 }}>
            Tip: when you start the backend via <code>run-dev.ps1</code>, copy
            the printed <code>MEALPILOT_URL</code>.
          </div>
        </div>

        <div className="card">
          <h3 className="cardTitle">Session</h3>
          <div className="row" style={{ marginTop: 10 }}>
            <span className="pill">JWT: {hasToken ? "set" : "missing"}</span>
            <Button variant="danger" onClick={() => logout()}>
              Logout
            </Button>
          </div>

          <div className="small" style={{ marginTop: 14 }}>
            Optional refresh token
          </div>
          <div className="row" style={{ marginTop: 8 }}>
            <label
              className="small"
              style={{ display: "flex", gap: 8, alignItems: "center" }}
            >
              <input
                type="checkbox"
                checked={enableRefresh}
                onChange={(e) => setEnableRefresh(e.target.checked)}
              />
              Enable refresh flow (requires backend `/api/auth/refresh`)
            </label>
          </div>

          <div style={{ marginTop: 10 }}>
            <Field
              label="Refresh token"
              error={
                enableRefresh && !refreshToken
                  ? "Required if refresh is enabled"
                  : null
              }
            >
              <input
                value={refreshToken}
                onChange={(e) => setRefreshToken(e.target.value)}
                placeholder="(optional)"
              />
            </Field>
          </div>

          <div className="small" style={{ marginTop: 12 }}>
            Developer tools
          </div>
          <div className="row" style={{ marginTop: 8 }}>
            <Link className="btn" to="/dev">
              Dev console
            </Link>
          </div>
        </div>
      </div>
    </AppShell>
  );
}
