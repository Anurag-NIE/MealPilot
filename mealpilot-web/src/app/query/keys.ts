export const qk = {
  items: () => ["items"] as const,
  decisions: (filter: { hasFeedback?: boolean | null }) =>
    ["decisions", filter] as const,
  decision: (id: string) => ["decision", id] as const,
  preferences: () => ["preferences"] as const,
};
