import React, { useEffect, useMemo } from "react";
import { z } from "zod";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { AppShell } from "../layout/AppShell";
import { Field } from "../../ui/Field";
import { Button } from "../../ui/Button";
import { Skeleton } from "../../ui/Skeleton";
import { parseCsvTags } from "../../utils";
import { pushToast } from "../store/toastStore";
import {
  usePreferencesQuery,
  useUpdatePreferenceProfileMutation,
} from "../query/usePreferences";

const schema = z.object({
  budgetMin: z.string().optional(),
  budgetMax: z.string().optional(),
  preferTags: z.string().optional(),
  avoidTags: z.string().optional(),
  preferRestaurants: z.string().optional(),
  avoidRestaurants: z.string().optional(),
  dietaryRestrictions: z.string().optional(),
  allergens: z.string().optional(),
  notes: z.string().optional(),
});

type FormValues = z.infer<typeof schema>;

function toOptionalInt(v: string | undefined): number | null {
  const s = (v ?? "").trim();
  if (!s) return null;
  const n = Number(s);
  return Number.isFinite(n) ? Math.trunc(n) : null;
}

function joinCsv(v: unknown): string {
  if (!Array.isArray(v)) return "";
  return v
    .filter((x): x is string => typeof x === "string" && Boolean(x.trim()))
    .join(", ");
}

export function PreferencesPage() {
  const prefQ = usePreferencesQuery();
  const saveM = useUpdatePreferenceProfileMutation();

  const profile = prefQ.data?.profile ?? null;

  const defaults = useMemo<FormValues>(
    () => ({
      budgetMin: profile?.budgetMin != null ? String(profile.budgetMin) : "",
      budgetMax: profile?.budgetMax != null ? String(profile.budgetMax) : "",
      preferTags: joinCsv(profile?.preferTags),
      avoidTags: joinCsv(profile?.avoidTags),
      preferRestaurants: joinCsv(profile?.preferRestaurants),
      avoidRestaurants: joinCsv(profile?.avoidRestaurants),
      dietaryRestrictions: joinCsv(profile?.dietaryRestrictions),
      allergens: joinCsv(profile?.allergens),
      notes: typeof profile?.notes === "string" ? profile.notes : "",
    }),
    [
      profile?.budgetMin,
      profile?.budgetMax,
      profile?.preferTags,
      profile?.avoidTags,
      profile?.preferRestaurants,
      profile?.avoidRestaurants,
      profile?.dietaryRestrictions,
      profile?.allergens,
      profile?.notes,
    ],
  );

  const {
    register,
    handleSubmit,
    reset,
    formState: { isDirty, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: defaults,
  });

  useEffect(() => {
    reset(defaults);
  }, [defaults, reset]);

  const busy = prefQ.isFetching || saveM.isPending || isSubmitting;

  return (
    <AppShell title="Preferences">
      <div className="grid2">
        <div className="card">
          <div className="row" style={{ justifyContent: "space-between" }}>
            <h3 className="cardTitle" style={{ marginBottom: 0 }}>
              Explicit profile
            </h3>
            <div className="row">
              <Button disabled={busy} onClick={() => prefQ.refetch()}>
                Reload
              </Button>
            </div>
          </div>

          {prefQ.isError ? (
            <div className="errorBox" style={{ marginTop: 10 }}>
              {(prefQ.error as any)?.message ?? "Request failed"}
            </div>
          ) : null}

          {prefQ.isLoading ? (
            <div style={{ marginTop: 12 }} className="stack">
              <Skeleton height={18} width={220} />
              <Skeleton height={40} />
              <Skeleton height={40} />
              <Skeleton height={40} />
              <Skeleton height={40} />
            </div>
          ) : (
            <form
              style={{ marginTop: 12 }}
              onSubmit={handleSubmit(async (v) => {
                const payload = {
                  budgetMin: toOptionalInt(v.budgetMin),
                  budgetMax: toOptionalInt(v.budgetMax),
                  preferTags: parseCsvTags(v.preferTags || ""),
                  avoidTags: parseCsvTags(v.avoidTags || ""),
                  preferRestaurants: parseCsvTags(v.preferRestaurants || ""),
                  avoidRestaurants: parseCsvTags(v.avoidRestaurants || ""),
                  dietaryRestrictions: parseCsvTags(
                    v.dietaryRestrictions || "",
                  ),
                  allergens: parseCsvTags(v.allergens || ""),
                  notes: (v.notes || "").trim() || null,
                };

                try {
                  await saveM.mutateAsync(payload);
                  pushToast({
                    kind: "success",
                    title: "Saved",
                    message: "Preference profile updated.",
                  });
                } catch (e: any) {
                  pushToast({
                    kind: "error",
                    title: "Save failed",
                    message: e?.message ?? "Request failed",
                  });
                }
              })}
            >
              <div className="grid2" style={{ gap: 12 }}>
                <Field label="Budget min">
                  <input
                    inputMode="numeric"
                    placeholder="150"
                    {...register("budgetMin")}
                  />
                </Field>
                <Field label="Budget max">
                  <input
                    inputMode="numeric"
                    placeholder="300"
                    {...register("budgetMax")}
                  />
                </Field>
              </div>

              <div className="grid2" style={{ gap: 12, marginTop: 12 }}>
                <Field label="Prefer tags (csv)">
                  <input
                    placeholder="veg, comfort"
                    {...register("preferTags")}
                  />
                </Field>
                <Field label="Avoid tags (csv)">
                  <input placeholder="peanut" {...register("avoidTags")} />
                </Field>
              </div>

              <div className="grid2" style={{ gap: 12, marginTop: 12 }}>
                <Field label="Prefer restaurants (csv)">
                  <input
                    placeholder="Punjabi Dhaba"
                    {...register("preferRestaurants")}
                  />
                </Field>
                <Field label="Avoid restaurants (csv)">
                  <input
                    placeholder="Fancy Place"
                    {...register("avoidRestaurants")}
                  />
                </Field>
              </div>

              <div className="grid2" style={{ gap: 12, marginTop: 12 }}>
                <Field label="Dietary restrictions (csv)">
                  <input
                    placeholder="veg"
                    {...register("dietaryRestrictions")}
                  />
                </Field>
                <Field label="Allergens (csv)">
                  <input placeholder="peanut" {...register("allergens")} />
                </Field>
              </div>

              <div style={{ marginTop: 12 }}>
                <Field label="Notes">
                  <textarea
                    placeholder="Prefer lighter dinners on weekdays"
                    {...register("notes")}
                  />
                </Field>
              </div>

              <div
                className="row"
                style={{ marginTop: 12, justifyContent: "space-between" }}
              >
                <div className="small">
                  Used to hard-filter candidates during Decide.
                </div>
                <div className="row">
                  <Button
                    type="button"
                    disabled={busy || !isDirty}
                    onClick={() => reset(defaults)}
                  >
                    Reset
                  </Button>
                  <Button
                    type="submit"
                    variant="primary"
                    disabled={busy || !isDirty}
                  >
                    Save
                  </Button>
                </div>
              </div>
            </form>
          )}
        </div>

        <div className="card">
          <h3 className="cardTitle">Learned signals</h3>
          <p className="small">
            These are updated automatically from your feedback and used to bias
            future decisions.
          </p>

          {prefQ.isLoading ? (
            <div className="stack" style={{ marginTop: 10 }}>
              <Skeleton height={18} width={180} />
              <Skeleton height={14} width={260} />
              <Skeleton height={14} width={240} />
              <Skeleton height={14} width={220} />
            </div>
          ) : (
            <div style={{ marginTop: 10 }} className="stack">
              <div className="row">
                <span className="pill">
                  Price penalty: {prefQ.data?.pricePenalty ?? 0}
                </span>
                <span className="pill">
                  Updated:{" "}
                  {prefQ.data?.updatedAt
                    ? new Date(prefQ.data.updatedAt as any).toLocaleString()
                    : "—"}
                </span>
              </div>

              <div>
                <div className="small">Top tag weights</div>
                <pre style={{ marginTop: 6 }}>
                  {JSON.stringify(prefQ.data?.tagWeights ?? {}, null, 2)}
                </pre>
              </div>

              <div>
                <div className="small">Top restaurant weights</div>
                <pre style={{ marginTop: 6 }}>
                  {JSON.stringify(prefQ.data?.restaurantWeights ?? {}, null, 2)}
                </pre>
              </div>
            </div>
          )}
        </div>
      </div>
    </AppShell>
  );
}
