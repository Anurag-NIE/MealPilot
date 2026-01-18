import React, { useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import type { AuthResponse, UserAccount } from "../../types";
import { apiPost } from "../api/client";
import { ApiError } from "../api/errors";
import { AppShell } from "../layout/AppShell";
import { getJwtSubject } from "../auth/jwt";
import { useSessionStore } from "../store/sessionStore";
import { Button } from "../../ui/Button";
import { Field } from "../../ui/Field";

export function AuthPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const apiBase = useSessionStore((s) => s.apiBase);
  const setToken = useSessionStore((s) => s.setToken);

  const [output, setOutput] = useState<string>("");

  const schema = useMemo(
    () =>
      z.object({
        username: z
          .string()
          .trim()
          .min(3, "username must be at least 3 characters")
          .max(50, "username must be <= 50 characters"),
        password: z
          .string()
          .min(8, "password must be at least 8 characters")
          .max(72, "password must be <= 72 characters"),
      }),
    [],
  );

  const {
    register,
    handleSubmit,
    setValue,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<{ username: string; password: string }>({
    resolver: zodResolver(schema),
    defaultValues: { username: "", password: "" },
    mode: "onSubmit",
  });

  function appendOutput(obj: unknown) {
    const s = typeof obj === "string" ? obj : JSON.stringify(obj, null, 2);
    setOutput((prev) => (prev ? prev + "\n" + s : s));
  }

  const from = (location.state as any)?.from as string | undefined;
  const goNext = (token: string) => {
    setToken(token);
    if (!token) return;

    const sub = getJwtSubject(token);
    const key = sub ? `mealpilot.onboarding.seen.v1.${sub}` : null;
    const hasSeen = key ? window.localStorage.getItem(key) === "1" : false;

    // First-login assisted onboarding: redirect to Preferences once per user.
    if (key && !hasSeen) {
      window.localStorage.setItem(key, "1");
      navigate("/preferences", { replace: true });
      return;
    }

    navigate(from || "/decide");
  };

  function applyFieldErrors(e: unknown) {
    if (!(e instanceof ApiError)) return;
    if (!e.fieldErrors?.length) return;

    for (const fe of e.fieldErrors) {
      if (fe.field === "username")
        setError("username", { type: "server", message: fe.message });
      if (fe.field === "password")
        setError("password", { type: "server", message: fe.message });
    }
  }

  async function doRegister(values: { username: string; password: string }) {
    try {
      const r = await apiPost<UserAccount>("/api/auth/register", values);
      appendOutput({ step: "register", status: 201, body: r });
    } catch (e) {
      applyFieldErrors(e);
      appendOutput({
        step: "register",
        error: (e as any)?.message ?? String(e),
      });
      throw e;
    }
  }

  async function doLogin(values: { username: string; password: string }) {
    try {
      const r = await apiPost<AuthResponse>("/api/auth/login", values);
      appendOutput({ step: "login", status: 200, body: r });
      if (r?.accessToken) goNext(r.accessToken);
    } catch (e) {
      applyFieldErrors(e);
      appendOutput({ step: "login", error: (e as any)?.message ?? String(e) });
      throw e;
    }
  }

  return (
    <AppShell title="Auth">
      <div className="grid2" style={{ alignItems: "start" }}>
        <div className="card">
          <h2 style={{ margin: 0, fontSize: 18, letterSpacing: 0.2 }}>
            Welcome back
          </h2>
          <div className="small" style={{ marginTop: 6 }}>
            Login or create an account. Backend: {apiBase}
          </div>

          <form style={{ marginTop: 12 }}>
            <div className="row">
              <div style={{ flex: 1, minWidth: 240 }}>
                <Field
                  label="Username"
                  error={errors.username?.message ?? null}
                >
                  <input placeholder="demo_user" {...register("username")} />
                </Field>
              </div>
              <div style={{ flex: 1, minWidth: 240 }}>
                <Field
                  label="Password"
                  error={errors.password?.message ?? null}
                >
                  <input
                    type="password"
                    placeholder="Password123!"
                    {...register("password")}
                  />
                </Field>
              </div>
            </div>

            <div className="row" style={{ marginTop: 10 }}>
              <Button
                type="button"
                disabled={isSubmitting}
                onClick={() => {
                  const ts = new Date()
                    .toISOString()
                    .replace(/[-:.TZ]/g, "")
                    .slice(0, 14);
                  setValue("username", "demo_" + ts, { shouldDirty: true });
                  setValue("password", "Password123!", { shouldDirty: true });
                  appendOutput("Generated demo credentials.");
                }}
              >
                Generate demo creds
              </Button>

              <Button
                type="button"
                variant="primary"
                disabled={isSubmitting}
                onClick={handleSubmit(doRegister)}
              >
                Create account
              </Button>

              <Button
                type="button"
                variant="primary"
                disabled={isSubmitting}
                onClick={handleSubmit(doLogin)}
              >
                Login
              </Button>

              <Button
                type="button"
                variant="danger"
                disabled={isSubmitting}
                onClick={() => goNext("")}
              >
                Logout
              </Button>
            </div>
          </form>

          <div className="small" style={{ marginTop: 10 }}>
            Tip: use “Generate demo creds” for a quick start.
          </div>
        </div>

        <div className="card">
          <h3 className="cardTitle">Next steps</h3>
          <p className="small">
            After login: add a few Items → then Decide will rank them → History
            stores results.
          </p>
          <div className="small" style={{ marginTop: 12 }}>
            Debug output
          </div>
          <pre style={{ marginTop: 8 }}>{output || "Ready."}</pre>
        </div>
      </div>
    </AppShell>
  );
}
