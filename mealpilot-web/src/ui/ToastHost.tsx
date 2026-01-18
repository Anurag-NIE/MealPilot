import React, { useEffect } from "react";
import { useToastStore } from "../app/store/toastStore";

function kindLabel(kind: string) {
  if (kind === "success") return "Success";
  if (kind === "warning") return "Warning";
  if (kind === "error") return "Error";
  return "Info";
}

export function ToastHost() {
  const toasts = useToastStore((s) => s.toasts);
  const remove = useToastStore((s) => s.remove);

  useEffect(() => {
    const timers = toasts.map((t) => {
      const elapsed = Date.now() - t.createdAtMs;
      const remaining = Math.max(250, t.durationMs - elapsed);
      return window.setTimeout(() => remove(t.id), remaining);
    });

    return () => timers.forEach((id) => window.clearTimeout(id));
  }, [toasts, remove]);

  if (!toasts.length) return null;

  return (
    <div className="toastStack" role="region" aria-label="Notifications">
      {toasts.map((t) => (
        <div key={t.id} className={`toast toast-${t.kind}`} role="status">
          <div className="toastHeader">
            <div className="toastTitle">{t.title ?? kindLabel(t.kind)}</div>
            <button
              className="toastClose"
              onClick={() => remove(t.id)}
              aria-label="Dismiss"
            >
              ×
            </button>
          </div>
          <div className="toastBody">{t.message}</div>
        </div>
      ))}
    </div>
  );
}
