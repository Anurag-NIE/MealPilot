import React from "react";
import { Navigate } from "react-router-dom";
import { Skeleton } from "../../ui/Skeleton";
import { useItemsQueryEnabled } from "../query/useItems";
import { usePreferencesQuery } from "../query/usePreferences";

function isEmptyObjectMap(v: unknown): boolean {
  if (!v || typeof v !== "object") return true;
  return Object.keys(v as any).length === 0;
}

function isProfileEmpty(profile: any): boolean {
  if (!profile) return true;

  const hasNum = (x: any) => typeof x === "number" && Number.isFinite(x);
  const hasStr = (x: any) => typeof x === "string" && Boolean(x.trim());
  const hasArr = (x: any) => Array.isArray(x) && x.length > 0;

  return !(
    hasNum(profile.budgetMin) ||
    hasNum(profile.budgetMax) ||
    hasArr(profile.preferTags) ||
    hasArr(profile.avoidTags) ||
    hasArr(profile.preferRestaurants) ||
    hasArr(profile.avoidRestaurants) ||
    hasArr(profile.dietaryRestrictions) ||
    hasArr(profile.allergens) ||
    hasStr(profile.notes)
  );
}

function isColdStartPreferences(pref: any): boolean {
  if (!pref) return true;
  const profileEmpty = isProfileEmpty(pref.profile);
  const learnedEmpty =
    isEmptyObjectMap(pref.tagWeights) &&
    isEmptyObjectMap(pref.restaurantWeights);
  const pricePenalty =
    typeof pref.pricePenalty === "number" ? pref.pricePenalty : 0;
  return profileEmpty && learnedEmpty && pricePenalty === 0;
}

export function DecideGate(props: { children: React.ReactNode }) {
  const itemsQ = useItemsQueryEnabled(true);
  const prefQ = usePreferencesQuery();

  const loading = itemsQ.isLoading || prefQ.isLoading;
  const ready = itemsQ.isSuccess && prefQ.isSuccess;

  if (loading) {
    return (
      <div className="card">
        <div className="stack">
          <Skeleton height={18} width={180} />
          <Skeleton height={14} width={320} />
          <Skeleton height={14} width={240} />
        </div>
      </div>
    );
  }

  if (ready) {
    const items = itemsQ.data ?? [];
    const noItems = items.length === 0;
    const coldPrefs = isColdStartPreferences(prefQ.data);

    // Spec: if no items + no prefs, block /decide and redirect to /items.
    if (noItems && coldPrefs) {
      return <Navigate to="/items" replace />;
    }
  }

  return <>{props.children}</>;
}
