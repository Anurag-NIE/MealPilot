import React, { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import type { DecideResponse } from "../../types";
import { parseCsvTags } from "../../utils";
import { ApiError } from "../api/errors";
import { apiPost } from "../api/client";
import { useDecideMutation } from "../query/useDecide";
import { AppShell } from "../layout/AppShell";
import { Button } from "../../ui/Button";
import { Field } from "../../ui/Field";

export function DecidePage() {
  const [lastDecision, setLastDecision] = useState<DecideResponse | null>(null);
  const [output, setOutput] = useState("");

  function appendOutput(obj: unknown) {
    const s = typeof obj === "string" ? obj : JSON.stringify(obj, null, 2);
    setOutput((prev) => (prev ? prev + "\n" + s : s));
  }

  const schema = z.object({
    budget: z
      .string()
      .optional()
      .refine((v) => !v || /^\d+$/.test(v), "budget must be a number"),
    mustTags: z.string().optional(),
    avoidTags: z.string().optional(),
    query: z.string().max(200, "query must be <= 200 characters").optional(),
    limit: z
      .string()
      .optional()
      .refine(
        (v) => !v || /^[1-9]\d*$/.test(v),
        "limit must be a positive integer",
      )
      .refine((v) => {
        if (!v) return true;
        const n = Number(v);
        return Number.isFinite(n) && n >= 1 && n <= 50;
      }, "limit must be between 1 and 50"),
  });

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: {
      budget: "320",
      mustTags: "spicy",
      avoidTags: "vegan",
      query: "",
      limit: "3",
    },
  });

  const decideM = useDecideMutation();
  const busy = decideM.isPending;

  const topPick = useMemo(
    () => lastDecision?.candidates?.[0] ?? null,
    [lastDecision],
  );

  function applyFieldErrors(e: unknown) {
    if (!(e instanceof ApiError)) return;
    for (const fe of e.fieldErrors) {
      if (fe.field === "budget")
        setError("budget", { type: "server", message: fe.message });
      if (fe.field === "mustHaveTags")
        setError("mustTags", { type: "server", message: fe.message });
      if (fe.field === "avoidTags")
        setError("avoidTags", { type: "server", message: fe.message });
      if (fe.field === "query")
        setError("query", { type: "server", message: fe.message });
      if (fe.field === "limit")
        setError("limit", { type: "server", message: fe.message });
    }
  }

  return (
    <AppShell title="Decide">
      <div className="grid2">
        <div className="card">
          <h3 className="cardTitle">Decide</h3>

          <form
            onSubmit={handleSubmit(async (values) => {
              try {
                const body = {
                  budget: values.budget ? Number(values.budget) : null,
                  mustHaveTags: parseCsvTags(values.mustTags || ""),
                  avoidTags: parseCsvTags(values.avoidTags || ""),
                  query: values.query || "",
                  limit: values.limit ? Number(values.limit) : null,
                };

                const r = await decideM.mutateAsync(body);
                setLastDecision(r);
                appendOutput({ step: "decide", status: 200, body: r });
              } catch (e) {
                applyFieldErrors(e);
                appendOutput({
                  step: "decide",
                  error: (e as any)?.message ?? String(e),
                });
              }
            })}
          >
            <div className="row">
              <div style={{ flex: 1, minWidth: 140 }}>
                <Field label="Budget" error={errors.budget?.message ?? null}>
                  <input {...register("budget")} />
                </Field>
              </div>
              <div style={{ flex: 2, minWidth: 240 }}>
                <Field
                  label="Must-have tags (csv)"
                  error={errors.mustTags?.message ?? null}
                >
                  <input {...register("mustTags")} />
                </Field>
              </div>
            </div>

            <div className="row" style={{ marginTop: 10 }}>
              <div style={{ flex: 2, minWidth: 240 }}>
                <Field
                  label="Avoid tags (csv)"
                  error={errors.avoidTags?.message ?? null}
                >
                  <input {...register("avoidTags")} />
                </Field>
              </div>
              <div style={{ flex: 2, minWidth: 240 }}>
                <Field label="Query" error={errors.query?.message ?? null}>
                  <input {...register("query")} />
                </Field>
              </div>
              <div style={{ flex: 1, minWidth: 120 }}>
                <Field
                  label="Limit (1-50)"
                  error={errors.limit?.message ?? null}
                >
                  <input {...register("limit")} />
                </Field>
              </div>
            </div>

            <div className="row" style={{ marginTop: 12 }}>
              <Button variant="primary" type="submit" disabled={busy}>
                Decide
              </Button>
            </div>
          </form>

          <div className="small" style={{ marginTop: 10 }}>
            Tip: add a few items first for best results.
          </div>
        </div>

        <div className="stack">
          <div className="card">
            <h3 className="cardTitle">Result</h3>

            {!lastDecision ? (
              <div className="empty">
                <div className="emptyTitle">No decision yet</div>
                <div className="small">Run Decide to get 1–3 ranked picks.</div>
              </div>
            ) : (
              <>
                <div className="row">
                  <span className="pill">
                    decisionId: {lastDecision.decisionId ?? "none"}
                  </span>
                  <span className="pill">limit: {lastDecision.limit}</span>
                </div>

                <div style={{ marginTop: 12 }} className="stack">
                  {lastDecision.candidates.map((c: any) => (
                    <div
                      key={c.item.id}
                      className={
                        c.item.id === topPick?.item.id
                          ? "pickCard pickTop"
                          : "pickCard"
                      }
                    >
                      <div className="pickTitle">{c.item.name}</div>
                      <div className="small">
                        score {c.score.toFixed(2)} · confidence{" "}
                        {(c.confidence * 100).toFixed(0)}%
                      </div>

                      {Array.isArray(c.deepLinks) && c.deepLinks.length ? (
                        <div
                          className="row"
                          style={{
                            marginTop: 8,
                            gap: 8,
                            flexWrap: "wrap" as any,
                          }}
                        >
                          {c.deepLinks.map((dl: any) => (
                            <a
                              key={String(dl.platform) + String(dl.url)}
                              className="btn"
                              href={dl.url}
                              target="_blank"
                              rel="noreferrer"
                              onClick={() => {
                                const decisionId = lastDecision.decisionId;
                                if (!decisionId || !dl?.platform) return;
                                void apiPost(
                                  `/api/decisions/${decisionId}/events`,
                                  {
                                    action: "CLICK_PLATFORM",
                                    platform: dl.platform,
                                  },
                                ).catch(() => {});
                              }}
                            >
                              Open {dl.platform}
                            </a>
                          ))}
                        </div>
                      ) : null}

                      {c.why?.length ? (
                        <ul
                          style={{
                            marginTop: 8,
                            marginBottom: 0,
                            paddingLeft: 18,
                          }}
                        >
                          {c.why.slice(0, 4).map((w: any, idx: number) => (
                            <li key={idx} className="small">
                              {w}
                            </li>
                          ))}
                        </ul>
                      ) : null}
                    </div>
                  ))}
                </div>
              </>
            )}

            <details style={{ marginTop: 12 }}>
              <summary className="small">Debug (raw JSON)</summary>
              <pre style={{ marginTop: 8 }}>{output || "Ready."}</pre>
            </details>
          </div>

          <div className="card">
            <h3 className="cardTitle" style={{ marginTop: 0 }}>
              Tips
            </h3>
            <ul style={{ margin: 0, paddingLeft: 18 }}>
              <li className="small">Add 3–10 items for best picks.</li>
              <li className="small">
                Use tags like “spicy”, “comfort”, “veg”.
              </li>
              <li className="small">
                Set your defaults in Preferences to speed this up.
              </li>
              <li className="small">
                Try a short query like “tikka” or “biryani”.
              </li>
            </ul>
          </div>
        </div>
      </div>
    </AppShell>
  );
}
