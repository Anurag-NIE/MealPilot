import { useMutation, useQuery } from "@tanstack/react-query";
import type { Decision, FeedbackStatus } from "../../types";
import { apiGet, apiPost } from "../api/client";
import { qk } from "./keys";

export function useDecisionsQuery(filter: { hasFeedback?: boolean | null }) {
  const params = new URLSearchParams();
  params.set("limit", "10");
  if (filter.hasFeedback === true) params.set("hasFeedback", "true");
  if (filter.hasFeedback === false) params.set("hasFeedback", "false");

  const path = `/api/decisions?${params.toString()}`;

  return useQuery({
    queryKey: qk.decisions(filter),
    queryFn: () => apiGet<Decision[]>(path),
  });
}

export function useDecisionQuery(id: string) {
  return useQuery({
    queryKey: qk.decision(id),
    enabled: Boolean(id),
    queryFn: () => apiGet<Decision>(`/api/decisions/${encodeURIComponent(id)}`),
  });
}

export function useSendFeedbackMutation() {
  return useMutation({
    mutationFn: (args: {
      decisionId: string;
      status: FeedbackStatus;
      reasonCode?: string | null;
      category?: string | null;
      tags?: string[] | null;
      rating?: number | null;
      comment?: string | null;
    }) =>
      apiPost<Decision>(`/api/decisions/${args.decisionId}/feedback`, {
        status: args.status,
        reasonCode: args.reasonCode ?? null,
        category: args.category ?? null,
        tags: args.tags ?? null,
        rating: args.rating ?? null,
        comment: args.comment ?? null,
      }),
  });
}
