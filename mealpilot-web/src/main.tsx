import React from "react";
import ReactDOM from "react-dom/client";
import { RouterProvider } from "react-router-dom";
import { QueryClientProvider } from "@tanstack/react-query";
import { AppBootstrap } from "./app/AppBootstrap";
import { queryClient } from "./app/query/queryClient";
import { router } from "./app/router";
import { ErrorBoundary } from "./ui/ErrorBoundary";
import { ToastHost } from "./ui/ToastHost";
import "./styles.css";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <AppBootstrap>
          <RouterProvider router={router} />
        </AppBootstrap>
      </QueryClientProvider>
      <ToastHost />
    </ErrorBoundary>
  </React.StrictMode>,
);
