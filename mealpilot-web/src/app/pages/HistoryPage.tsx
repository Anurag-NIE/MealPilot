import React, { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import type { FeedbackStatus } from "../../types";
import { AppShell } from "../layout/AppShell";
import {
  useDecisionsQuery,
  useSendFeedbackMutation,
} from "../query/useDecisions";
import { Button } from "../../ui/Button";
import { Field } from "../../ui/Field";
import { Skeleton } from "../../ui/Skeleton";
import { parseCsvTags } from "../../utils";

function formatIsoShort(iso: string): string {
  try {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return iso;
    return d.toLocaleString();
  } catch {
    return iso;
  }
}

export function HistoryPage() {
  const [feedbackStatus, setFeedbackStatus] =
    useState<FeedbackStatus>("ACCEPT");
  const [feedbackReason, setFeedbackReason] = useState("tasty");
  const [feedbackCategory, setFeedbackCategory] = useState("");
  const [feedbackTags, setFeedbackTags] = useState("");
  const [feedbackRating, setFeedbackRating] = useState("");
  const [feedbackComment, setFeedbackComment] = useState("");

  const [filter, setFilter] = useState<"all" | "hasFeedback" | "noFeedback">(
    "all",
  );

  const hasFeedback =
    filter === "hasFeedback" ? true : filter === "noFeedback" ? false : null;
  const listQ = useDecisionsQuery({ hasFeedback });
  const feedbackM = useSendFeedbackMutation();
  const busy = listQ.isFetching || feedbackM.isPending;

  return (
    <AppShell title="History">
      <div className="grid2">
        <div className="card">
          <div className="row" style={{ justifyContent: "space-between" }}>
            <h3 className="cardTitle" style={{ marginBottom: 0 }}>
              Decision history
            </h3>
            <div className="row">
              <Button disabled={busy} onClick={() => listQ.refetch()}>
                Load
              </Button>
            </div>
          </div>

          <div className="row" style={{ marginTop: 10 }}>
            <div style={{ minWidth: 220 }}>
              <Field label="Filter">
                <select
                  value={filter}
                  onChange={(e) => setFilter(e.target.value as any)}
                >
                  <option value="all">All</option>
                  <option value="hasFeedback">Has feedback</option>
                  <option value="noFeedback">No feedback</option>
                </select>
              </Field>
            </div>
          </div>

          {listQ.isError ? (
            <div className="errorBox" style={{ marginTop: 10 }}>
              {(listQ.error as any)?.message ?? "Request failed"}
            </div>
          ) : null}

          {(listQ.data?.length ?? 0) === 0 ? (
            <div className="empty" style={{ marginTop: 12 }}>
              <div className="emptyTitle">No decisions loaded</div>
              <div className="small">
                Run Decide first, then come back and click Load.
              </div>
            </div>
          ) : listQ.isLoading ? (
            <div style={{ marginTop: 12 }} className="stack">
              <Skeleton height={52} />
              <Skeleton height={52} />
              <Skeleton height={52} />
            </div>
          ) : (
            <div style={{ marginTop: 12 }} className="stack">
              {(listQ.data ?? []).map((d) => (
                <div key={d.id} className="listRow">
                  <div>
                    <div className="listTitle">
                      {formatIsoShort(d.createdAt)}
                    </div>
                    <div className="small">
                      id: {d.id} · feedback: {d.feedback?.status ?? "none"}
                    </div>
                  </div>
                  <div className="row">
                    <Link
                      className="btn"
                      to={`/history/${encodeURIComponent(d.id)}`}
                    >
                      View
                    </Link>
                    <Button
                      variant="primary"
                      disabled={busy}
                      onClick={() =>
                        feedbackM
                          .mutateAsync({
                            decisionId: d.id,
                            status: feedbackStatus,
                            reasonCode: feedbackReason,
                            category: feedbackCategory || null,
                            tags: parseCsvTags(feedbackTags || ""),
                            rating: feedbackRating.trim()
                              ? Number(feedbackRating.trim())
                              : null,
                            comment: feedbackComment,
                          })
                          .then(() => listQ.refetch())
                          .catch(() => {})
                      }
                    >
                      {d.feedback ? "Update feedback" : "Send feedback"}
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="card">
          <h3 className="cardTitle">Feedback</h3>
          <p className="small">
            Select status/reason/comment, then click “Send feedback” on any
            decision.
          </p>

          <div style={{ marginTop: 10 }}>
            <Field label="Status">
              <select
                value={feedbackStatus}
                onChange={(e) =>
                  setFeedbackStatus(e.target.value as FeedbackStatus)
                }
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
                value={feedbackReason}
                onChange={(e) => setFeedbackReason(e.target.value)}
                placeholder="tasty"
              />
            </Field>
          </div>

          <div style={{ marginTop: 10 }}>
            <Field label="Category">
              <select
                value={feedbackCategory}
                onChange={(e) => setFeedbackCategory(e.target.value)}
              >
                <option value="">(none)</option>
                <option value="PRICE">PRICE</option>
                <option value="TASTE">TASTE</option>
                <option value="DIET">DIET</option>
                <option value="AVAILABILITY">AVAILABILITY</option>
                <option value="VARIETY">VARIETY</option>
                <option value="OTHER">OTHER</option>
              </select>
            </Field>
          </div>

          <div style={{ marginTop: 10 }}>
            <Field label="Tags (csv)">
              <input
                value={feedbackTags}
                onChange={(e) => setFeedbackTags(e.target.value)}
                placeholder="budget, spicy"
              />
            </Field>
          </div>

          <div style={{ marginTop: 10 }}>
            <Field label="Rating (1-5)">
              <input
                inputMode="numeric"
                value={feedbackRating}
                onChange={(e) => setFeedbackRating(e.target.value)}
                placeholder="2"
              />
            </Field>
          </div>

          <div style={{ marginTop: 10 }}>
            <Field label="Comment (optional)">
              <input
                value={feedbackComment}
                onChange={(e) => setFeedbackComment(e.target.value)}
                placeholder="optional"
              />
            </Field>
          </div>

          <div className="small" style={{ marginTop: 12 }}>
            Feedback updates user preferences and biases the next Decide.
          </div>
        </div>
      </div>
    </AppShell>
  );
}
