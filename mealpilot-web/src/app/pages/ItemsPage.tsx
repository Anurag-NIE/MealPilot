import React, { useMemo } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import type { Item } from "../../types";
import { parseCsvTags } from "../../utils";
import { ApiError } from "../api/errors";
import {
  useCreateItemMutation,
  useDeleteItemMutation,
  useItemsQuery,
} from "../query/useItems";
import { AppShell } from "../layout/AppShell";
import { Button } from "../../ui/Button";
import { Field } from "../../ui/Field";

export function ItemsPage() {
  const schema = z.object({
    name: z
      .string()
      .trim()
      .min(2, "name must be at least 2 characters")
      .max(120, "name must be <= 120 characters"),
    restaurantName: z
      .string()
      .trim()
      .max(120, "restaurant must be <= 120 characters")
      .optional()
      .or(z.literal("")),
    tags: z.string().optional(),
    platformHints: z.string().optional(),
    priceEstimate: z
      .string()
      .optional()
      .refine((v) => !v || /^\d+$/.test(v), "price must be a number"),
  });

  const {
    register,
    handleSubmit,
    setError,
    setValue,
    formState: { errors },
  } = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: "",
      restaurantName: "",
      tags: "",
      platformHints: "swiggy, zomato",
      priceEstimate: "",
    },
  });

  const itemsQ = useItemsQuery();
  const createM = useCreateItemMutation();
  const deleteM = useDeleteItemMutation();

  const busy = itemsQ.isFetching || createM.isPending || deleteM.isPending;
  const rawItems = itemsQ.data ?? [];
  const activeItems = useMemo(
    () => rawItems.filter((i) => i.active),
    [rawItems],
  );

  function applyFieldErrors(e: unknown) {
    if (!(e instanceof ApiError)) return;
    for (const fe of e.fieldErrors) {
      if (fe.field === "name")
        setError("name", { type: "server", message: fe.message });
      if (fe.field === "restaurantName")
        setError("restaurantName", { type: "server", message: fe.message });
      if (fe.field === "tags")
        setError("tags", { type: "server", message: fe.message });
      if (fe.field === "priceEstimate")
        setError("priceEstimate", { type: "server", message: fe.message });
    }
  }

  return (
    <AppShell title="Items">
      <div className="grid2">
        <div className="card">
          <h3 className="cardTitle">Add item</h3>

          {itemsQ.isError ? (
            <div className="errorBox">
              {(itemsQ.error as any)?.message ?? "Failed to load items"}
            </div>
          ) : null}
          {createM.isError ? (
            <div className="errorBox">
              {(createM.error as any)?.message ?? "Failed to create item"}
            </div>
          ) : null}

          <form
            onSubmit={handleSubmit(async (values) => {
              try {
                await createM.mutateAsync({
                  name: values.name.trim(),
                  restaurantName: values.restaurantName?.trim()
                    ? values.restaurantName.trim()
                    : null,
                  tags: parseCsvTags(values.tags || ""),
                  platformHints: parseCsvTags(values.platformHints || ""),
                  priceEstimate: values.priceEstimate
                    ? Number(values.priceEstimate)
                    : null,
                });
                setValue("name", "");
                setValue("restaurantName", "");
                setValue("tags", "");
                setValue("platformHints", "swiggy, zomato");
                setValue("priceEstimate", "");
              } catch (e) {
                applyFieldErrors(e);
              }
            })}
          >
            <div style={{ marginTop: 10 }}>
              <Field label="Name" error={errors.name?.message ?? null}>
                <input placeholder="Paneer Tikka" {...register("name")} />
              </Field>
            </div>

            <div className="row" style={{ marginTop: 10 }}>
              <div style={{ flex: 2, minWidth: 220 }}>
                <Field
                  label="Restaurant (optional)"
                  error={errors.restaurantName?.message ?? null}
                >
                  <input
                    placeholder="Tandoori House"
                    {...register("restaurantName")}
                  />
                </Field>
              </div>
              <div style={{ flex: 1, minWidth: 140 }}>
                <Field
                  label="Price (optional)"
                  error={errors.priceEstimate?.message ?? null}
                >
                  <input placeholder="300" {...register("priceEstimate")} />
                </Field>
              </div>
            </div>

            <div style={{ marginTop: 10 }}>
              <Field label="Tags (csv)" error={errors.tags?.message ?? null}>
                <input placeholder="spicy, protein" {...register("tags")} />
              </Field>
            </div>

            <div style={{ marginTop: 10 }}>
              <Field
                label="Platform hints (csv)"
                error={errors.platformHints?.message ?? null}
              >
                <input
                  placeholder="swiggy, zomato"
                  {...register("platformHints")}
                />
              </Field>
            </div>

            <div className="row" style={{ marginTop: 12 }}>
              <Button variant="primary" type="submit" disabled={busy}>
                Add
              </Button>

              <Button
                type="button"
                disabled={busy}
                onClick={() =>
                  createM
                    .mutateAsync({
                      name: "Chicken Biryani",
                      restaurantName: "Spice Hub",
                      tags: ["spicy", "rice"],
                      platformHints: ["swiggy", "zomato"],
                      priceEstimate: 350,
                    })
                    .catch(() => {})
                }
              >
                Add sample
              </Button>

              <Button
                type="button"
                disabled={busy}
                onClick={() => itemsQ.refetch()}
              >
                Refresh
              </Button>
            </div>

            <div className="small" style={{ marginTop: 12 }}>
              Next: go to Decide to rank items.
            </div>
          </form>
        </div>

        <div className="card">
          <div className="row" style={{ justifyContent: "space-between" }}>
            <h3 className="cardTitle" style={{ marginBottom: 0 }}>
              Your items
            </h3>
            <Button disabled={busy} onClick={() => itemsQ.refetch()}>
              Load
            </Button>
          </div>

          {activeItems.length === 0 ? (
            <div className="empty" style={{ marginTop: 12 }}>
              <div className="emptyTitle">No items yet</div>
              <div className="small">
                Add a few meals so Decide has candidates.
              </div>
            </div>
          ) : (
            <div style={{ marginTop: 12 }} className="stack">
              {activeItems.map((it: Item) => (
                <div key={it.id} className="listRow">
                  <div>
                    <div className="listTitle">{it.name}</div>
                    <div className="small">
                      {(it.restaurantName ? it.restaurantName + " · " : "") +
                        (it.tags?.join(", ") || "no tags")}
                      {typeof it.priceEstimate === "number"
                        ? ` · ₹${it.priceEstimate}`
                        : ""}
                    </div>
                  </div>
                  <div className="row">
                    <Button
                      variant="danger"
                      disabled={busy}
                      onClick={() => deleteM.mutateAsync(it.id).catch(() => {})}
                    >
                      Delete
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}

          <div className="small" style={{ marginTop: 12 }}>
            Deleted items are soft-deleted on the backend.
          </div>
        </div>
      </div>
    </AppShell>
  );
}
