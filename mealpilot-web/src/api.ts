export type ApiResponse<T> = {
  ok: boolean;
  status: number;
  headers: Headers;
  data: T | null;
  rawText: string;
};

export async function requestJson<T>(
  baseUrl: string,
  path: string,
  options: RequestInit & { token?: string } = {}
): Promise<ApiResponse<T>> {
  const url = baseUrl.replace(/\/$/, "") + path;

  const headers: Record<string, string> = {
    Accept: "application/json",
    ...(options.headers as Record<string, string> | undefined),
  };

  if (options.body && !headers["Content-Type"]) {
    headers["Content-Type"] = "application/json";
  }

  if (options.token) {
    headers.Authorization = `Bearer ${options.token}`;
  }

  const res = await fetch(url, {
    ...options,
    headers,
  });

  const rawText = await res.text();
  let data: T | null = null;
  try {
    data = rawText ? (JSON.parse(rawText) as T) : null;
  } catch {
    data = null;
  }

  return {
    ok: res.ok,
    status: res.status,
    headers: res.headers,
    data,
    rawText,
  };
}

export function tokenPreview(token: string): string {
  if (token.length <= 24) return token;
  return token.slice(0, 12) + "..." + token.slice(-12);
}
