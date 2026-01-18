import React from "react";
import clsx from "clsx";

export function Button(
  props: React.ButtonHTMLAttributes<HTMLButtonElement> & {
    variant?: "default" | "primary" | "danger";
  },
) {
  const { variant = "default", className, ...rest } = props;

  return (
    <button
      {...rest}
      className={clsx(
        "btn",
        variant === "primary" && "primary",
        variant === "danger" && "danger",
        className,
      )}
    />
  );
}
