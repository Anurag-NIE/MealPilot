import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { UserPreference } from "../../types";
import { apiGet, apiPut } from "../api/client";
import { qk } from "./keys";

export function usePreferencesQuery() {
  return useQuery({
    queryKey: qk.preferences(),
    queryFn: () => apiGet<UserPreference>("/api/preferences"),
  });
}

export type UpdateProfileArgs = {
  budgetMin?: number | null;
  budgetMax?: number | null;
  preferTags?: string[] | null;
  avoidTags?: string[] | null;
  preferRestaurants?: string[] | null;
  avoidRestaurants?: string[] | null;
  dietaryRestrictions?: string[] | null;
  allergens?: string[] | null;
  notes?: string | null;
};

export function useUpdatePreferenceProfileMutation() {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: (body: UpdateProfileArgs) =>
      apiPut<UserPreference>("/api/preferences/profile", body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: qk.preferences() });
    },
  });
}
