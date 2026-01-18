import createClient from "openapi-fetch";
import type { paths } from "./api-types";

export function createMealpilotClient(opts: {
  baseUrl: string;
  token?: string;
}) {
  return createClient<paths>({
    baseUrl: opts.baseUrl,
    headers: opts.token ? { Authorization: `Bearer ${opts.token}` } : undefined,
  });
}
