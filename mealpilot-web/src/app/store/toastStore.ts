import { create } from "zustand";

export type ToastKind = "info" | "success" | "warning" | "error";

export type Toast = {
  id: string;
  kind: ToastKind;
  title?: string;
  message: string;
  createdAtMs: number;
  durationMs: number;
};

function randomId(): string {
  try {
    // eslint-disable-next-line @typescript-eslint/no-unsafe-member-access
    return (
      (globalThis.crypto as any)?.randomUUID?.() ??
      String(Date.now()) + Math.random().toString(16).slice(2)
    );
  } catch {
    return String(Date.now()) + Math.random().toString(16).slice(2);
  }
}

export type ToastState = {
  toasts: Toast[];
  push: (t: {
    kind: ToastKind;
    title?: string;
    message: string;
    durationMs?: number;
  }) => string;
  remove: (id: string) => void;
  clear: () => void;
};

export const useToastStore = create<ToastState>((set, get) => ({
  toasts: [],

  push: (t) => {
    const id = randomId();
    const toast: Toast = {
      id,
      kind: t.kind,
      title: t.title,
      message: t.message,
      createdAtMs: Date.now(),
      durationMs: typeof t.durationMs === "number" ? t.durationMs : 3500,
    };

    set({ toasts: [...get().toasts, toast] });
    return id;
  },

  remove: (id) => set({ toasts: get().toasts.filter((t) => t.id !== id) }),

  clear: () => set({ toasts: [] }),
}));

export function pushToast(t: {
  kind: ToastKind;
  title?: string;
  message: string;
  durationMs?: number;
}) {
  return useToastStore.getState().push(t);
}
