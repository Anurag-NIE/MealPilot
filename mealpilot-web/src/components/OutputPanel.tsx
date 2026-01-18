import React from "react";

export function OutputPanel(props: { output: string; onClear: () => void }) {
  return (
    <div className="card">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h3 style={{ marginTop: 0, marginBottom: 0 }}>Output</h3>
        <button onClick={props.onClear}>Clear</button>
      </div>
      <pre style={{ marginTop: 12 }}>{props.output || "Ready."}</pre>
    </div>
  );
}
