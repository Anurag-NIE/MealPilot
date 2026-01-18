import React from "react";

type State = { hasError: boolean; errorMessage: string };

export class ErrorBoundary extends React.Component<
  { children: React.ReactNode },
  State
> {
  state: State = { hasError: false, errorMessage: "" };

  static getDerivedStateFromError(error: unknown): State {
    const msg = error instanceof Error ? error.message : String(error);
    return { hasError: true, errorMessage: msg };
  }

  componentDidCatch(error: unknown) {
    // Intentionally no console spam in prod; React will log in dev.
    void error;
  }

  render() {
    if (!this.state.hasError) return this.props.children;

    return (
      <div className="container" style={{ paddingBottom: 96 }}>
        <div className="card">
          <h2 style={{ marginTop: 0 }}>Something went wrong</h2>
          <div className="small">
            Try reloading the app. If it keeps happening, check the console for
            details.
          </div>
          <div className="row" style={{ marginTop: 12 }}>
            <button
              className="primary"
              onClick={() => window.location.reload()}
            >
              Reload
            </button>
            <a className="btn" href="/">
              Home
            </a>
          </div>

          <details style={{ marginTop: 12 }}>
            <summary className="small">Error details</summary>
            <pre style={{ marginTop: 8 }}>
              {this.state.errorMessage || "Unknown error"}
            </pre>
          </details>
        </div>
      </div>
    );
  }
}
