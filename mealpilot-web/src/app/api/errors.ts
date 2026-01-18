export type ApiFieldError = {
  field: string;
  message: string;
};

export type ApiErrorResponse = {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  path?: string;
  requestId?: string;
  fieldErrors?: ApiFieldError[] | null;
};

export class ApiError extends Error {
  status: number;
  fieldErrors: ApiFieldError[];
  raw: unknown;

  constructor(opts: {
    status: number;
    message: string;
    fieldErrors?: ApiFieldError[];
    raw?: unknown;
  }) {
    super(opts.message);
    this.name = "ApiError";
    this.status = opts.status;
    this.fieldErrors = opts.fieldErrors ?? [];
    this.raw = opts.raw;
  }
}

export function coerceApiError(
  status: number,
  rawText: string,
  parsed: unknown,
): ApiError {
  const maybe = parsed as ApiErrorResponse | null;
  const msg =
    typeof maybe?.message === "string" && maybe.message.trim()
      ? maybe.message
      : rawText || `HTTP ${status}`;
  const fieldErrors = Array.isArray(maybe?.fieldErrors)
    ? maybe!.fieldErrors!.filter((e): e is ApiFieldError =>
        Boolean(
          e && typeof e.field === "string" && typeof e.message === "string",
        ),
      )
    : [];

  return new ApiError({
    status,
    message: msg,
    fieldErrors,
    raw: parsed ?? rawText,
  });
}
