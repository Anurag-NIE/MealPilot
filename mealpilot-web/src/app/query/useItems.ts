import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { Item } from "../../types";
import { apiDelete, apiGet, apiPost } from "../api/client";
import { qk } from "./keys";

export type CreateItemRequest = {
  name: string;
  restaurantName?: string | null;
  tags?: string[];
  platformHints?: string[];
  priceEstimate?: number | null;
};

export function useItemsQuery() {
  return useQuery({
    queryKey: qk.items(),
    queryFn: () => apiGet<Item[]>("/api/items"),
  });
}

export function useItemsQueryEnabled(enabled: boolean) {
  return useQuery({
    queryKey: qk.items(),
    queryFn: () => apiGet<Item[]>("/api/items"),
    enabled,
  });
}

export function useCreateItemMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CreateItemRequest) => apiPost<Item>("/api/items", req),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: qk.items() });
    },
  });
}

export function useDeleteItemMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiDelete(`/api/items/${id}`),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: qk.items() });
    },
  });
}
