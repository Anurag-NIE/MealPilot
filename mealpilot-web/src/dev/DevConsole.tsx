import React, { useMemo, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { tokenPreview } from "../api";
import { FlowStatePanel } from "../components/FlowStatePanel";
import { OutputPanel } from "../components/OutputPanel";
import { apiGetWithHeaders, apiPost } from "../app/api/client";
import type {
  AuthResponse,
  DecideResponse,
  FeedbackStatus,
  UserAccount,
} from "../types";
import { parseCsvTags } from "../utils";
import { useSessionStore } from "../app/store/sessionStore";
import {
  useCreateItemMutation,
  useItemsQueryEnabled,
} from "../app/query/useItems";
import { useDecideMutation } from "../app/query/useDecide";
import { useDecisionsInfinite } from "../app/query/useDecisionsInfinite";
import { Button } from "../ui/Button";
import { Field } from "../ui/Field";
import type { Tab } from "../types";

export function DevConsole() {
  const [tab, setTab] = useState<Tab>("auth");
  const [lastDecision, setLastDecision] = useState<DecideResponse | null>(null);

  const [feedbackStatus, setFeedbackStatus] =
    useState<FeedbackStatus>("ACCEPT");
  const [feedbackReason, setFeedbackReason] = useState<string>("tasty");
  const [feedbackComment, setFeedbackComment] = useState<string>("");
  const [output, setOutput] = useState<string>("");

  const qc = useQueryClient();

  const apiBase = useSessionStore((s) => s.apiBase);
  const setApiBase = useSessionStore((s) => s.setApiBase);
  const token = useSessionStore((s) => s.token);
  const setToken = useSessionStore((s) => s.setToken);
  const logout = useSessionStore((s) => s.logout);
  const hasToken = useSessionStore((s) => s.hasToken);

  const itemsQ = useItemsQueryEnabled(hasToken);
  const createItemM = useCreateItemMutation();
  const decideM = useDecideMutation();
  const decisionsIQ = useDecisionsInfinite(hasToken);

  const items = itemsQ.data ?? [];
  const decisions = useMemo(
    () => decisionsIQ.data?.pages.flatMap((p) => p.data) ?? [],
    [decisionsIQ.data],
  );
  const nextCursor = decisionsIQ.data?.pages.at(-1)?.nextCursor ?? "";

  const busy =
    itemsQ.isFetching ||
    createItemM.isPending ||
    decideM.isPending ||
    decisionsIQ.isFetching ||
    decisionsIQ.isFetchingNextPage;

  function appendOutput(obj: unknown) {
    const s = typeof obj === "string" ? obj : JSON.stringify(obj, null, 2);
    setOutput((prev) => (prev ? prev + "\n" + s : s));
  }

  const authSchema = z.object({
    username: z.string().trim().min(3),
    password: z.string().min(8),
  });

  const authForm = useForm<{ username: string; password: string }>({
    resolver: zodResolver(authSchema),
    defaultValues: { username: "", password: "" },
  });

  const decideSchema = z.object({
    budget: z.string().optional(),
    mustTags: z.string().optional(),
    avoidTags: z.string().optional(),
    query: z.string().optional(),
    limit: z.string().optional(),
  });

  const decideForm = useForm<z.infer<typeof decideSchema>>({
    resolver: zodResolver(decideSchema),
    defaultValues: {
      budget: "320",
      mustTags: "spicy",
      avoidTags: "vegan",
      query: "tikka",
      limit: "3",
    },
  });

  async function runDemoFlow() {
    const ts = new Date()
      .toISOString()
      .replace(/[-:.TZ]/g, "")
      .slice(0, 14);
    const demoUsername = `demo_${ts}`;
    const demoPassword = "Password123!";

    setTab("auth");
    authForm.setValue("username", demoUsername);
    authForm.setValue("password", demoPassword);
    appendOutput({
      step: "demo",
      message: "Starting demo flow",
      username: demoUsername,
    });

    try {
      const registerRes = await apiPost<UserAccount>("/api/auth/register", {
        username: demoUsername,
        password: demoPassword,
      });
      appendOutput({ step: "register", status: 200, body: registerRes });

      const loginRes = await apiPost<AuthResponse>("/api/auth/login", {
        username: demoUsername,
        password: demoPassword,
      });
      appendOutput({ step: "login", status: 200, body: loginRes });
      if (!loginRes?.accessToken) return;
      setToken(loginRes.accessToken);

      setTab("items");
      const samples = [
        {
          name: "Chicken Biryani",
          restaurantName: "Spice Hub",
          tags: ["spicy", "rice"],
          priceEstimate: 350,
        },
        {
          name: "Veggie Bowl",
          restaurantName: "Green Kitchen",
          tags: ["healthy", "vegan"],
          priceEstimate: 250,
        },
        {
          name: "Paneer Tikka",
          restaurantName: "Tandoori House",
          tags: ["spicy", "protein"],
          priceEstimate: 300,
        },
      ];

      for (const it of samples) {
        const r = await createItemM.mutateAsync(it);
        appendOutput({ step: "createItem", status: 200, body: r });
      }

      await itemsQ.refetch();
      appendOutput({
        step: "listItems",
        status: 200,
        count: (itemsQ.data ?? []).length,
      });

      setTab("decide");
      const d = await decideM.mutateAsync({
        budget: 320,
        mustHaveTags: ["spicy"],
        avoidTags: ["vegan"],
        query: "tikka",
        limit: 3,
      });
      setLastDecision(d);
      appendOutput({ step: "decide", status: 200, body: d });

      setTab("decisions");
      await qc.removeQueries({ queryKey: ["dev", "decisions", apiBase] });
      await decisionsIQ.refetch();
      appendOutput({
        step: "listDecisions",
        status: 200,
        xNextCursor: decisionsIQ.data?.pages.at(-1)?.nextCursor ?? "",
        count: decisionsIQ.data?.pages?.[0]?.data?.length ?? 0,
      });
    } catch (e: any) {
      appendOutput({ step: "demo", error: e?.message ?? String(e) });
    }
  }

  const authWarn = !hasToken
    ? "Login required for /api/items, /api/decide, /api/decisions."
    : null;

  return (
    <div className="container">
      <div className="header">
        <div>
          <h1 className="title">MealPilot</h1>
          <div className="subtitle">
            React dev UI for the full flow (register → login → items → decide →
            decisions → feedback)
          </div>
        </div>

        <div className="row">
          <span className="badge">API: {apiBase}</span>
          <span className="badge">
            JWT: {token ? tokenPreview(token) : "none"}
          </span>
        </div>
      </div>

      <div className="card" style={{ marginBottom: 14 }}>
        <div className="row">
          <div style={{ flex: 1, minWidth: 280 }}>
            <label>Backend base URL</label>
            <input
              value={apiBase}
              onChange={(e) => setApiBase(e.target.value)}
              placeholder="http://localhost:9000"
            />
            <div className="small">
              Tip: use the MEALPILOT_URL printed by run-dev.ps1
            </div>
          </div>

          <button
            className="primary"
            disabled={busy}
            onClick={async () => {
              try {
                const r = await apiGetWithHeaders<any>("/actuator/health");
                appendOutput({
                  step: "health",
                  body: r.data,
                });
              } catch (e) {
                appendOutput({
                  step: "health",
                  error: String(e),
                });
              }
            }}
          >
            Check health
          </button>

          <button disabled={busy} onClick={() => setOutput("")}>
            Clear output
          </button>
        </div>
      </div>

      <div className="tabs" style={{ marginBottom: 14 }}>
        <button
          className={tab === "auth" ? "tab active" : "tab"}
          onClick={() => setTab("auth")}
        >
          Auth
        </button>
        <button
          className={tab === "items" ? "tab active" : "tab"}
          onClick={() => setTab("items")}
        >
          Items
        </button>
        <button
          className={tab === "decide" ? "tab active" : "tab"}
          onClick={() => setTab("decide")}
        >
          Decide
        </button>
        <button
          className={tab === "decisions" ? "tab active" : "tab"}
          onClick={() => setTab("decisions")}
        >
          Decisions
        </button>
      </div>

      <div className="grid">
        <div className="card">
          {tab === "auth" && (
            <>
              <h3 style={{ marginTop: 0 }}>Auth</h3>
              <div className="row" style={{ marginTop: 10 }}>
                <div style={{ flex: 1, minWidth: 240 }}>
                  <Field
                    label="Username"
                    error={authForm.formState.errors.username?.message ?? null}
                  >
                    <input
                      {...authForm.register("username")}
                      placeholder="demo_user"
                    />
                  </Field>
                </div>
                <div style={{ flex: 1, minWidth: 240 }}>
                  <Field
                    label="Password"
                    error={authForm.formState.errors.password?.message ?? null}
                  >
                    <input
                      type="password"
                      {...authForm.register("password")}
                      placeholder="Password123!"
                    />
                  </Field>
                </div>
              </div>

              <div className="row" style={{ marginTop: 10 }}>
                <Button
                  type="button"
                  disabled={busy}
                  onClick={() => {
                    const ts = new Date()
                      .toISOString()
                      .replace(/[-:.TZ]/g, "")
                      .slice(0, 14);
                    authForm.setValue("username", "demo_" + ts);
                    authForm.setValue("password", "Password123!");
                    appendOutput("Generated demo credentials.");
                  }}
                >
                  Generate demo creds
                </Button>

                <Button
                  type="button"
                  variant="primary"
                  disabled={busy}
                  onClick={authForm.handleSubmit(async (v) => {
                    const r = await apiPost<UserAccount>(
                      "/api/auth/register",
                      v,
                    );
                    appendOutput({ step: "register", status: 200, body: r });
                  })}
                >
                  Register
                </Button>

                <Button
                  type="button"
                  variant="primary"
                  disabled={busy}
                  onClick={authForm.handleSubmit(async (v) => {
                    const r = await apiPost<AuthResponse>("/api/auth/login", v);
                    appendOutput({ step: "login", status: 200, body: r });
                    if (r?.accessToken) setToken(r.accessToken);
                  })}
                >
                  Login (save JWT)
                </Button>

                <Button
                  type="button"
                  variant="danger"
                  disabled={busy}
                  onClick={() => logout()}
                >
                  Logout
                </Button>
              </div>

              <div className="small" style={{ marginTop: 8 }}>
                {authWarn ??
                  "JWT is stored in localStorage (via Zustand persist)."}
              </div>
            </>
          )}

          {tab === "items" && (
            <>
              <h3 style={{ marginTop: 0 }}>Items</h3>
              <div className="row">
                <Button
                  variant="primary"
                  disabled={busy || !hasToken}
                  onClick={async () => {
                    const r = await itemsQ.refetch();
                    appendOutput({
                      step: "listItems",
                      status: r.isSuccess ? 200 : 500,
                      count: (r.data ?? []).length,
                    });
                  }}
                >
                  Load items
                </Button>

                <Button
                  disabled={busy || !hasToken}
                  onClick={async () => {
                    const samples = [
                      {
                        name: "Chicken Biryani",
                        restaurantName: "Spice Hub",
                        tags: ["spicy", "rice"],
                        priceEstimate: 350,
                      },
                      {
                        name: "Veggie Bowl",
                        restaurantName: "Green Kitchen",
                        tags: ["healthy", "vegan"],
                        priceEstimate: 250,
                      },
                      {
                        name: "Paneer Tikka",
                        restaurantName: "Tandoori House",
                        tags: ["spicy", "protein"],
                        priceEstimate: 300,
                      },
                    ];

                    for (const it of samples) {
                      const r = await createItemM.mutateAsync(it);
                      appendOutput({
                        step: "createItem",
                        status: 200,
                        body: r,
                      });
                    }
                    await itemsQ.refetch();
                  }}
                >
                  Create sample items
                </Button>
              </div>

              <div className="small" style={{ marginTop: 8 }}>
                Items are per-user (based on the JWT subject). Loaded:{" "}
                {items.length}
              </div>
            </>
          )}

          {tab === "decide" && (
            <>
              <h3 style={{ marginTop: 0 }}>Decide</h3>

              <div className="row">
                <div style={{ flex: 1, minWidth: 140 }}>
                  <Field label="Budget">
                    <input {...decideForm.register("budget")} />
                  </Field>
                </div>
                <div style={{ flex: 2, minWidth: 240 }}>
                  <Field label="Must-have tags (csv)">
                    <input {...decideForm.register("mustTags")} />
                  </Field>
                </div>
              </div>

              <div className="row">
                <div style={{ flex: 2, minWidth: 240 }}>
                  <Field label="Avoid tags (csv)">
                    <input {...decideForm.register("avoidTags")} />
                  </Field>
                </div>
                <div style={{ flex: 2, minWidth: 240 }}>
                  <Field label="Query">
                    <input {...decideForm.register("query")} />
                  </Field>
                </div>
                <div style={{ flex: 1, minWidth: 120 }}>
                  <Field label="Limit (1-3)">
                    <input {...decideForm.register("limit")} />
                  </Field>
                </div>
              </div>

              <div className="row" style={{ marginTop: 10 }}>
                <Button
                  variant="primary"
                  disabled={busy || !hasToken}
                  onClick={decideForm.handleSubmit(async (v) => {
                    const body = {
                      budget: v.budget ? Number(v.budget) : null,
                      mustHaveTags: parseCsvTags(v.mustTags || ""),
                      avoidTags: parseCsvTags(v.avoidTags || ""),
                      query: v.query || "",
                      limit: v.limit ? Number(v.limit) : null,
                    };

                    const r = await decideM.mutateAsync(body);
                    setLastDecision(r);
                    appendOutput({ step: "decide", status: 200, body: r });
                  })}
                >
                  Decide
                </Button>
              </div>

              {lastDecision?.decisionId && (
                <div className="small" style={{ marginTop: 8 }}>
                  Last decisionId: {lastDecision.decisionId}
                </div>
              )}
            </>
          )}

          {tab === "decisions" && (
            <>
              <h3 style={{ marginTop: 0 }}>Decisions</h3>
              <div className="row">
                <Button
                  variant="primary"
                  disabled={busy || !hasToken}
                  onClick={async () => {
                    await qc.removeQueries({
                      queryKey: ["dev", "decisions", apiBase],
                    });
                    await decisionsIQ.refetch();
                    appendOutput({
                      step: "listDecisions",
                      status: 200,
                      xNextCursor:
                        decisionsIQ.data?.pages.at(-1)?.nextCursor ?? "",
                      count: decisionsIQ.data?.pages?.[0]?.data?.length ?? 0,
                    });
                  }}
                >
                  Load decisions
                </Button>

                <Button
                  disabled={busy || !hasToken || !decisionsIQ.hasNextPage}
                  onClick={async () => {
                    const r = await decisionsIQ.fetchNextPage();
                    appendOutput({
                      step: "loadMore",
                      status: 200,
                      xNextCursor: r.data?.pages.at(-1)?.nextCursor ?? "",
                      total: r.data?.pages.flatMap((p) => p.data).length ?? 0,
                    });
                  }}
                >
                  Load more
                </Button>
              </div>

              <div style={{ marginTop: 12 }}>
                <Field label="Feedback status">
                  <select
                    value={feedbackStatus}
                    onChange={(e) =>
                      setFeedbackStatus(e.target.value as FeedbackStatus)
                    }
                  >
                    <option value="ACCEPT">ACCEPT</option>
                    <option value="REJECT">REJECT</option>
                  </select>
                </Field>
              </div>

              <div className="row">
                <div style={{ flex: 1, minWidth: 220 }}>
                  <Field label="Reason code">
                    <input
                      value={feedbackReason}
                      onChange={(e) => setFeedbackReason(e.target.value)}
                      placeholder="tasty"
                    />
                  </Field>
                </div>
                <div style={{ flex: 2, minWidth: 240 }}>
                  <Field label="Comment">
                    <input
                      value={feedbackComment}
                      onChange={(e) => setFeedbackComment(e.target.value)}
                      placeholder="optional"
                    />
                  </Field>
                </div>
              </div>

              <div className="row" style={{ marginTop: 10 }}>
                <Button
                  variant="primary"
                  disabled={busy || !hasToken || !lastDecision?.decisionId}
                  onClick={async () => {
                    if (!lastDecision?.decisionId) return;
                    const r = await apiPost<any>(
                      `/api/decisions/${lastDecision.decisionId}/feedback`,
                      {
                        status: feedbackStatus,
                        reasonCode: feedbackReason,
                        comment: feedbackComment,
                      },
                    );
                    appendOutput({ step: "feedback", status: 200, body: r });
                  }}
                >
                  Feedback (last decision)
                </Button>
              </div>

              <div className="small" style={{ marginTop: 10 }}>
                Loaded decisions in memory: {decisions.length} · X-Next-Cursor:{" "}
                {nextCursor || "none"}
              </div>
            </>
          )}
        </div>

        <div style={{ display: "grid", gap: 14 }}>
          <FlowStatePanel
            apiBase={apiBase}
            busy={busy}
            tab={tab}
            hasToken={hasToken}
            items={items}
            lastDecision={lastDecision}
            decisions={decisions}
            nextCursor={nextCursor}
            onTab={setTab}
            onRunDemoFlow={runDemoFlow}
          />
          <OutputPanel output={output} onClear={() => setOutput("")} />
        </div>
      </div>

      <div style={{ marginTop: 14 }} className="small">
        Backend provides a lightweight HTML landing at{" "}
        <a href={apiBase} target="_blank" rel="noreferrer">
          {apiBase}/
        </a>{" "}
        too.
      </div>
    </div>
  );
}
