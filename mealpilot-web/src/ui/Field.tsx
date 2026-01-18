import React from "react";

export function Field(props: {
  label: string;
  children: React.ReactNode;
  error?: string | null;
}) {
  return (
    <div className="field">
      <label>{props.label}</label>
      {props.children}
      {props.error ? (
        <div className="small" style={{ color: "#ff9a9a", marginTop: 6 }}>
          {props.error}
        </div>
      ) : null}
    </div>
  );
}
