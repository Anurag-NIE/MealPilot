import { useCallback, useState } from "react";

export type AsyncState<T> = {
  loading: boolean;
  error: string | null;
  data: T | null;
};

export function useAsync<T>() {
  const [state, setState] = useState<AsyncState<T>>({
    loading: false,
    error: null,
    data: null,
  });

  const run = useCallback(async (fn: () => Promise<T>) => {
    setState({ loading: true, error: null, data: null });
    try {
      const data = await fn();
      setState({ loading: false, error: null, data });
      return data;
    } catch (e: any) {
      const msg = typeof e?.message === "string" ? e.message : "Request failed";
      setState({ loading: false, error: msg, data: null });
      throw e;
    }
  }, []);

  return { state, run, setState };
}
