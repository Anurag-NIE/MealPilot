import React, { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import type { FeedbackReasonCategory, FeedbackStatus } from "../../types";
import { parseCsvTags } from "../../utils";
import { AppShell } from "../layout/AppShell";
import {
  useDecisionQuery,
  useSendFeedbackMutation,
} from "../query/useDecisions";
import { apiPost } from "../api/client";
import { Button } from "../../ui/Button";
import { Field } from "../../ui/Field";
import { Skeleton } from "../../ui/Skeleton";
import { pushToast } from "../store/toastStore";

const categories: Array<FeedbackReasonCategory> = [
  "PRICE",
  "TASTE",
  "DIET",
  "AVAILABILITY",
  "VARIETY",
  "OTHER",
];

export function HistoryDetailPage() {
  const { id } = useParams();
  const decisionId = typeof id === "string" ? id : "";

  const dQ = useDecisionQuery(decisionId);
  const feedbackM = useSendFeedbackMutation();

  const existing = dQ.data?.feedback ?? null;

  const [status, setStatus] = useState<FeedbackStatus>(
    (existing?.status as any) ?? "SKIP",
  );
  const [reasonCode, setReasonCode] = useState<string>(
    existing?.reasonCode ?? "",
  );
  const [category, setCategory] = useState<FeedbackReasonCategory | "">(
    ((existing as any)?.reason?.category as any) ?? "",
  );
  const [tagsCsv, setTagsCsv] = useState<string>(
    Array.isArray((existing as any)?.reason?.tags)
      ? (existing as any).reason.tags.join(", ")
      : "",
  );
  const [rating, setRating] = useState<string>(
    typeof (existing as any)?.rating === "number"
      ? String((existing as any).rating)
      : "",
  );
  const [comment, setComment] = useState<string>(existing?.comment ?? "");

  const busy = dQ.isFetching || feedbackM.isPending;

  const candidates = useMemo(
    () => dQ.data?.candidates ?? [],
    [dQ.data?.candidates],
  );

  function trackClickPlatform(platform: string | null | undefined) {
    if (!decisionId || !platform) return;
    void apiPost(`/api/decisions/${decisionId}/events`, {
      action: "CLICK_PLATFORM",
      platform,
    }).catch(() => {});
  }

  return (
    <AppShell title="History detail">
      <div className="grid2">
        <div className="card">
          <div className="row" style={{ justifyContent: "space-between" }}>
            <div className="row">
              <Link className="btn" to="/history">
                Back
              </Link>
              <span className="pill">id: {decisionId || "—"}</span>
            </div>
            <div className="row">
              <Button disabled={busy} onClick={() => dQ.refetch()}>
                Reload
              </Button>
            </div>
          </div>

          {dQ.isError ? (
            <div className="errorBox" style={{ marginTop: 10 }}>
              {(dQ.error as any)?.message ?? "Request failed"}
            </div>
          ) : null}

          {dQ.isLoading ? (
            <div style={{ marginTop: 12 }} className="stack">
              <Skeleton height={18} width={240} />
              <Skeleton height={14} width={320} />
              <Skeleton height={14} width={280} />
              <Skeleton height={140} />
            </div>
          ) : (
            <div style={{ marginTop: 12 }} className="stack">
              <div className="row">
                <span className="pill">
                  Created:{" "}
                  {dQ.data?.createdAt
                    ? new Date(dQ.data.createdAt as any).toLocaleString()
                    : "—"}
                </span>
                <span className="pill">
                  Feedback: {dQ.data?.feedback?.status ?? "none"}
                </span>
              </div>

              <div>
                <div className="small">Candidates</div>
                {candidates.length === 0 ? (
                  <div className="small" style={{ marginTop: 6 }}>
                    No candidates stored on this decision.
                  </div>
                ) : (
                  <div className="stack" style={{ marginTop: 8 }}>
                    {candidates.map((c: any, idx: number) => (
                      <div key={c?.item?.id ?? idx} className="pickCard">
                        <div className="pickTitle">
                          {c?.item?.name ?? "(unknown)"}
                        </div>
                        <div className="small">
                          score{" "}
                          {typeof c?.score === "number"
                            ? c.score.toFixed(2)
                            : "—"}{" "}
                          · confidence{" "}
                          {typeof c?.confidence === "number"
                            ? (c.confidence * 100).toFixed(0) + "%"
                            : "—"}
                        </div>

                        {Array.isArray(c?.deepLinks) && c.deepLinks.length ? (
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
                                onClick={() => trackClickPlatform(dl.platform)}
                              >
                                Open {dl.platform}
                              </a>
                            ))}
                          </div>
                        ) : null}

                        {Array.isArray(c?.why) && c.why.length ? (
                          <ul
                            style={{
                              marginTop: 8,
                              marginBottom: 0,
                              paddingLeft: 18,
                            }}
                          >
                            {c.why.slice(0, 4).map((w: any, wIdx: number) => (
                              <li key={wIdx} className="small">
                                {w}
                              </li>
                            ))}
                          </ul>
                        ) : null}
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <div>
                <div className="small">Meta</div>
                <pre style={{ marginTop: 6 }}>
                  {JSON.stringify(dQ.data?.meta ?? {}, null, 2)}
                </pre>
              </div>
            </div>
          )}
        </div>

        <div className="card">
          <h3 className="cardTitle">Feedback</h3>
          <p className="small">
            Send structured feedback to improve future decisions.
          </p>

          <div style={{ marginTop: 10 }}>
            <Field label="Status">
              <select
                value={status}
                onChange={(e) => setStatus(e.target.value as FeedbackStatus)}
              >
                <option value="ACCEPT">ACCEPT</option>
                <option value="REJECT">REJECT</option>
                <option value="SKIP">SKIP</option>
              </select>
            </Field>
          </div>

          <div style={{ marginTop: 10 }}>
            <Field label="Reason code">
              <input
                value={reasonCode}
                onChange={(e) => setReasonCode(e.target.value)}
                placeholder="TOO_PRICEY"
              />
            </Field>
          </div>

          <div style={{ marginTop: 10 }}>
            <Field label="Category">
              <select
                value={category}
                onChange={(e) => setCategory(e.target.value as any)}
              >
                <option value="">(none)</option>
                {categories.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </Field>
          </div>

          <div style={{ marginTop: 10 }}>
            <Field label="Tags (csv)">
              <input
                value={tagsCsv}
                onChange={(e) => setTagsCsv(e.target.value)}
                placeholder="budget, spicy"
              />
            </Field>
          </div>

          <div style={{ marginTop: 10 }}>
            <Field label="Rating (1-5)">
              <input
                inputMode="numeric"
                value={rating}
                onChange={(e) => setRating(e.target.value)}
                placeholder="2"
              />
            </Field>
          </div>

          <div style={{ marginTop: 10 }}>
            <Field label="Comment (optional)">
              <textarea
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                placeholder="Too expensive today"
              />
            </Field>
          </div>

          <div
            className="row"
            style={{ marginTop: 12, justifyContent: "space-between" }}
          >
            <div className="small">SKIP is allowed (minimal signal).</div>
            <Button
              variant="primary"
              disabled={busy || !decisionId}
              onClick={() => {
                const ratingNum = rating.trim() ? Number(rating.trim()) : null;
                const payload = {
                  decisionId: decisionId,
                  status,
                  reasonCode: reasonCode.trim() || null,
                  category: category || null,
                  tags: parseCsvTags(tagsCsv || ""),
                  rating: Number.isFinite(ratingNum as any)
                    ? (ratingNum as any)
                    : null,
                  comment: comment.trim() || null,
                };

                feedbackM
                  .mutateAsync(payload as any)
                  .then(() => {
                    pushToast({
                      kind: "success",
                      title: "Saved",
                      message: "Feedback updated.",
                    });
                    return dQ.refetch();
                  })
                  .catch((e: any) => {
                    pushToast({
                      kind: "error",
                      title: "Save failed",
                      message: e?.message ?? "Request failed",
                    });
                  });
              }}
            >
              Save feedback
            </Button>
          </div>
        </div>
      </div>
    </AppShell>
  );
}
