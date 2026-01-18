import React from "react";
import { Link } from "react-router-dom";
import { AppShell } from "../layout/AppShell";
import { useSessionStore } from "../store/sessionStore";

export function LandingPage() {
  const hasToken = useSessionStore((s) => s.hasToken);

  return (
    <AppShell>
      <div className="dashboard">
        <div>
          <div className="hero">
            <div>
              <h1 className="heroTitle">Decide what to eat in seconds.</h1>
              <p className="heroSubtitle">
                MealPilot gives 1–3 ranked picks with confidence + reasons, then
                learns from your feedback.
              </p>
              <div className="row" style={{ marginTop: 14 }}>
                <Link
                  className="btn primary"
                  to={hasToken ? "/decide" : "/auth"}
                >
                  {hasToken ? "Start deciding" : "Get started"}
                </Link>
                <Link className="btn" to="/items">
                  Manage items
                </Link>
              </div>
            </div>
          </div>

          <div className="grid3" style={{ marginTop: 14 }}>
            <div className="card">
              <h3 className="cardTitle">Explainable picks</h3>
              <p className="small">
                Each suggestion includes “why” so you can trust the
                recommendation.
              </p>
            </div>
            <div className="card">
              <h3 className="cardTitle">Feedback loop</h3>
              <p className="small">
                Accept/reject + reason improves your next decisions.
              </p>
            </div>
            <div className="card">
              <h3 className="cardTitle">Fast history</h3>
              <p className="small">
                Cursor pagination keeps history scalable as you grow.
              </p>
            </div>
          </div>

          <div className="sectionHead">
            <div>
              <div className="sectionTitle">Quick actions</div>
              <div className="sectionSubtitle">
                Jump into the main flow without hunting around.
              </div>
            </div>
          </div>

          <div className="actionGrid">
            <Link className="card cardLink" to={hasToken ? "/decide" : "/auth"}>
              <h3 className="cardTitle">
                {hasToken ? "Decide now" : "Login / Register"}
              </h3>
              <div className="small">
                {hasToken
                  ? "Get 1–3 ranked picks in seconds."
                  : "Create an account and get a JWT."}
              </div>
            </Link>
            <Link className="card cardLink" to="/items">
              <h3 className="cardTitle">Manage items</h3>
              <div className="small">
                Add your go-to meals so Decide has candidates.
              </div>
            </Link>
            <Link className="card cardLink" to="/history">
              <h3 className="cardTitle">History</h3>
              <div className="small">
                Browse previous decisions and outcomes.
              </div>
            </Link>
            <Link className="card cardLink" to="/preferences">
              <h3 className="cardTitle">Preferences</h3>
              <div className="small">
                Tune tags, restaurants, diet/allergens, and notes.
              </div>
            </Link>
            <Link className="card cardLink" to="/settings">
              <h3 className="cardTitle">Settings</h3>
              <div className="small">
                API base URL, session tools, and diagnostics.
              </div>
            </Link>
            <Link className="card cardLink" to="/decide">
              <h3 className="cardTitle">Try Decide demo</h3>
              <div className="small">
                Even better after you’ve added a few items.
              </div>
            </Link>
          </div>
        </div>

        <aside className="dashAside">
          <div className="card" style={{ padding: 18 }}>
            <div className="small">Recommended flow</div>
            <ol style={{ marginTop: 10, marginBottom: 0, paddingLeft: 18 }}>
              <li>Login / Register</li>
              <li>Add a few go-to Items</li>
              <li>Decide → get 1–3 picks + decisionId</li>
              <li>History → browse decisions</li>
              <li>Feedback → teaches future picks</li>
            </ol>
          </div>

          <div className="card">
            <h3 className="cardTitle" style={{ marginTop: 0 }}>
              Setup tip
            </h3>
            <div className="small">
              If your backend is running on a different port, set it in
              Settings.
            </div>
          </div>
        </aside>
      </div>
    </AppShell>
  );
}
