import React from "react";
import { Link } from "react-router-dom";
import { AppShell } from "../layout/AppShell";

export function NotFoundPage() {
  return (
    <AppShell title="Not Found">
      <div className="card">
        <h3 className="cardTitle">Page not found</h3>
        <p className="small">The page you requested does not exist.</p>
        <Link className="btn primary" to="/">
          Go home
        </Link>
      </div>
    </AppShell>
  );
}
