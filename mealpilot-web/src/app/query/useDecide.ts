import { useMutation } from "@tanstack/react-query";
import type { DecideResponse } from "../../types";
import { apiPost } from "../api/client";

export type DecideRequest = {
  budget?: number | null;
  mustHaveTags?: string[] | null;
  avoidTags?: string[] | null;
  query?: string | null;
  limit?: number | null;
};

export function useDecideMutation() {
  return useMutation({
    mutationFn: (req: DecideRequest) =>
      apiPost<DecideResponse>("/api/decide", req),
  });
}
