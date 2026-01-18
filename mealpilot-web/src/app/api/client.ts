import { requestJson } from "../../api";
import { coerceApiError } from "./errors";
import { getSessionSnapshot } from "../store/sessionStore";
import { pushToast } from "../store/toastStore";

let refreshInFlight: Promise<string> | null = null;

function isPublicEndpoint(path: string): boolean {
  return path.startsWith("/api/auth/") || path === "/api/health";
}

async function tryRefreshAccessToken(): Promise<string> {
  const session = getSessionSnapshot();
  if (!session.enableRefresh) throw new Error("Refresh disabled");
  if (!session.refreshToken) throw new Error("No refresh token");

  // Optional endpoint (backend may not implement yet).
  const res = await requestJson<any>(session.apiBase, "/api/auth/refresh", {
    method: "POST",
    body: JSON.stringify({ refreshToken: session.refreshToken }),
  });

  if (!res.ok) throw coerceApiError(res.status, res.rawText, res.data);

  const next = res.data?.accessToken;
  if (typeof next !== "string" || !next)
    throw new Error("Refresh succeeded but no accessToken returned");

  session.setToken(next);
  return next;
}

async function getValidToken(): Promise<string> {
  const session = getSessionSnapshot();
  if (!session.token) return "";
  if (!session.isTokenExpired) return session.token;

  if (!session.enableRefresh || !session.refreshToken) {
    pushToast({
      kind: "warning",
      title: "Session expired",
      message: "Please log in again.",
    });
    session.logout();
    return "";
  }

  if (!refreshInFlight) {
    refreshInFlight = tryRefreshAccessToken().finally(() => {
      refreshInFlight = null;
    });
  }

  try {
    return await refreshInFlight;
  } catch {
    pushToast({
      kind: "warning",
      title: "Session expired",
      message: "Please log in again.",
    });
    session.logout();
    return "";
  }
}

function handleUnauthorized() {
  const session = getSessionSnapshot();
  if (session.token) {
    pushToast({
      kind: "warning",
      title: "Logged out",
      message: "Your session is no longer valid. Please log in again.",
    });
  }
  session.logout();
}

export async function apiGet<T>(path: string): Promise<T> {
  const session = getSessionSnapshot();
  const token = isPublicEndpoint(path) ? "" : await getValidToken();
  const res = await requestJson<T>(session.apiBase, path, {
    method: "GET",
    token: token || undefined,
  });

  if (res.status === 401 && !isPublicEndpoint(path)) handleUnauthorized();
  if (!res.ok) throw coerceApiError(res.status, res.rawText, res.data);
  return (res.data ?? null) as T;
}

export async function apiGetWithHeaders<T>(
  path: string,
): Promise<{ data: T; headers: Headers }> {
  const session = getSessionSnapshot();
  const token = isPublicEndpoint(path) ? "" : await getValidToken();
  const res = await requestJson<T>(session.apiBase, path, {
    method: "GET",
    token: token || undefined,
  });

  if (res.status === 401 && !isPublicEndpoint(path)) handleUnauthorized();
  if (!res.ok) throw coerceApiError(res.status, res.rawText, res.data);
  return { data: (res.data ?? null) as T, headers: res.headers };
}

export async function apiPost<T>(path: string, body: unknown): Promise<T> {
  const session = getSessionSnapshot();
  const token = isPublicEndpoint(path) ? "" : await getValidToken();
  const res = await requestJson<T>(session.apiBase, path, {
    method: "POST",
    token: token || undefined,
    body: JSON.stringify(body),
  });

  if (res.status === 401 && !isPublicEndpoint(path)) handleUnauthorized();
  if (!res.ok) throw coerceApiError(res.status, res.rawText, res.data);
  return (res.data ?? null) as T;
}

export async function apiPut<T>(path: string, body: unknown): Promise<T> {
  const session = getSessionSnapshot();
  const token = isPublicEndpoint(path) ? "" : await getValidToken();
  const res = await requestJson<T>(session.apiBase, path, {
    method: "PUT",
    token: token || undefined,
    body: JSON.stringify(body),
  });

  if (res.status === 401 && !isPublicEndpoint(path)) handleUnauthorized();
  if (!res.ok) throw coerceApiError(res.status, res.rawText, res.data);
  return (res.data ?? null) as T;
}

export async function apiDelete(path: string): Promise<void> {
  const session = getSessionSnapshot();
  const token = isPublicEndpoint(path) ? "" : await getValidToken();
  const res = await requestJson<any>(session.apiBase, path, {
    method: "DELETE",
    token: token || undefined,
  });

  if (res.status === 401 && !isPublicEndpoint(path)) handleUnauthorized();
  if (!res.ok) throw coerceApiError(res.status, res.rawText, res.data);
}
