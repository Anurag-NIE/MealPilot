import React from "react";
import clsx from "clsx";

export function Skeleton(props: {
  width?: number | string;
  height?: number | string;
  className?: string;
  style?: React.CSSProperties;
}) {
  const { width = "100%", height = 12, className, style } = props;
  return (
    <div
      className={clsx("skeleton", className)}
      style={{ width, height, ...style }}
      aria-hidden="true"
    />
  );
}
