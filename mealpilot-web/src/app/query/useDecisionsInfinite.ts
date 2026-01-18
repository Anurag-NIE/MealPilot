import { useInfiniteQuery } from "@tanstack/react-query";
import type { Decision } from "../../types";
import { apiGetWithHeaders } from "../api/client";
import { useSessionStore } from "../store/sessionStore";

type Page = {
  data: Decision[];
  nextCursor: string;
};

export function useDecisionsInfinite(enabled: boolean) {
  const apiBase = useSessionStore((s) => s.apiBase);

  return useInfiniteQuery({
    queryKey: ["dev", "decisions", apiBase],
    enabled,
    initialPageParam: "" as string,
    queryFn: async ({ pageParam }) => {
      const cursor = typeof pageParam === "string" ? pageParam : "";
      const path = cursor
        ? `/api/decisions?limit=10&cursor=${encodeURIComponent(cursor)}`
        : "/api/decisions?limit=10";

      const r = await apiGetWithHeaders<Decision[]>(path);
      return {
        data: r.data ?? [],
        nextCursor: r.headers.get("X-Next-Cursor") || "",
      } satisfies Page;
    },
    getNextPageParam: (lastPage) =>
      lastPage.nextCursor ? lastPage.nextCursor : undefined,
  });
}
