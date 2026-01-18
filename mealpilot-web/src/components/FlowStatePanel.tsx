import React from "react";
import type { DecideResponse, Decision, Item, Tab } from "../types";

function formatIsoShort(iso: string): string {
  try {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return iso;
    return d.toLocaleString();
  } catch {
    return iso;
  }
}

export function FlowStatePanel(props: {
  apiBase: string;
  busy: boolean;
  tab: Tab;
  hasToken: boolean;
  items: Item[];
  lastDecision: DecideResponse | null;
  decisions: Decision[];
  nextCursor: string;
  onTab: (t: Tab) => void;
  onRunDemoFlow: () => void;
}) {
  const {
    apiBase,
    busy,
    tab,
    hasToken,
    items,
    lastDecision,
    decisions,
    nextCursor,
    onTab,
    onRunDemoFlow,
  } = props;

  const hasItems = items.length > 0;
  const hasDecision = Boolean(lastDecision?.decisionId);
  const hasDecisions = decisions.length > 0;

  const nextStep: { label: string; tab: Tab } = !hasToken
    ? { label: "Login first", tab: "auth" }
    : !hasItems
      ? { label: "Create items", tab: "items" }
      : !hasDecision
        ? { label: "Run Decide", tab: "decide" }
        : { label: "Load decisions + feedback", tab: "decisions" };

  return (
    <div className="card">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h3 style={{ margin: 0 }}>Flow / State</h3>
        <button className="primary" disabled={busy} onClick={onRunDemoFlow}>
          Run demo flow
        </button>
      </div>

      <div className="small" style={{ marginTop: 8 }}>
        Backend:{" "}
        <a href={apiBase} target="_blank" rel="noreferrer">
          {apiBase}
        </a>
      </div>

      <div style={{ marginTop: 12 }}>
        <div className="small">Recommended next step</div>
        <div className="row" style={{ marginTop: 6 }}>
          <span className="badge">{nextStep.label}</span>
          <button disabled={busy} onClick={() => onTab(nextStep.tab)}>
            Go to {nextStep.tab}
          </button>
          {tab !== "auth" && !hasToken && (
            <span className="small">(You need a JWT to use most features)</span>
          )}
        </div>
      </div>

      <div style={{ marginTop: 14 }}>
        <div className="small">How features connect</div>
        <ol style={{ marginTop: 8, marginBottom: 0, paddingLeft: 18 }}>
          <li>
            <b>Auth</b> → get JWT
            <div className="small">
              register/login → token saved in localStorage
            </div>
          </li>
          <li>
            <b>Items</b> → your meal candidates
            <div className="small">
              items are per-user; Decide scores from these
            </div>
          </li>
          <li>
            <b>Decide</b> → creates a Decision
            <div className="small">returns ranked candidates + decisionId</div>
          </li>
          <li>
            <b>Decisions</b> → history + paging
            <div className="small">`X-Next-Cursor` drives “Load more”</div>
          </li>
          <li>
            <b>Feedback</b> → learning
            <div className="small">accept/reject influences future scoring</div>
          </li>
        </ol>
      </div>

      <div style={{ marginTop: 14 }} className="row">
        <span className="badge">JWT: {hasToken ? "set" : "missing"}</span>
        <span className="badge">Items: {items.length}</span>
        <span className="badge">
          Last decisionId: {lastDecision?.decisionId ?? "none"}
        </span>
        <span className="badge">Decisions loaded: {decisions.length}</span>
      </div>

      <div style={{ marginTop: 12 }}>
        <div className="small">Items (local state)</div>
        <div style={{ marginTop: 6 }}>
          {items.length === 0 ? (
            <div className="small">
              No items loaded yet. Use “Create sample items” then “Load items”.
            </div>
          ) : (
            <ul style={{ margin: 0, paddingLeft: 18 }}>
              {items.slice(0, 6).map((it) => (
                <li key={it.id}>
                  {it.name}
                  {it.restaurantName ? (
                    <span className="small"> — {it.restaurantName}</span>
                  ) : null}
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>

      <div style={{ marginTop: 12 }}>
        <div className="small">Last Decide result</div>
        <div style={{ marginTop: 6 }}>
          {!lastDecision ? (
            <div className="small">No Decide run yet.</div>
          ) : (
            <ul style={{ margin: 0, paddingLeft: 18 }}>
              {lastDecision.candidates.slice(0, 3).map((c: any) => (
                <li key={c.item.id}>
                  {c.item.name}
                  <span className="small">
                    {" "}
                    — score {c.score.toFixed(2)}, conf{" "}
                    {(c.confidence * 100).toFixed(0)}%
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>

      <div style={{ marginTop: 12 }}>
        <div className="small">Decisions (loaded)</div>
        <div style={{ marginTop: 6 }}>
          {!hasDecisions ? (
            <div className="small">
              No decisions loaded yet. Use “Load decisions”.
            </div>
          ) : (
            <ul style={{ margin: 0, paddingLeft: 18 }}>
              {decisions.slice(0, 5).map((d) => (
                <li key={d.id}>
                  {formatIsoShort(d.createdAt)}
                  <span className="small"> — {d.id}</span>
                  <span className="small">
                    {" "}
                    — feedback: {d.feedback?.status ?? "none"}
                  </span>
                </li>
              ))}
            </ul>
          )}
          <div className="small" style={{ marginTop: 6 }}>
            X-Next-Cursor: {nextCursor || "none"}
          </div>
        </div>
      </div>

      <div className="small" style={{ marginTop: 10 }}>
        Note: the “Output” panel shows raw API JSON so you can debug requests.
      </div>
    </div>
  );
}
