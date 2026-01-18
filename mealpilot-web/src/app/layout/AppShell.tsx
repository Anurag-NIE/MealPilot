import React from "react";
import { NavLink } from "react-router-dom";
import { tokenPreview } from "../../api";
import { useSessionStore } from "../store/sessionStore";

function NavItem(props: {
  to: string;
  label: string;
  icon: string;
  variant: "bottom" | "side";
}) {
  return (
    <NavLink
      to={props.to}
      className={({ isActive }) => {
        const base = props.variant === "side" ? "sideNavItem" : "navItem";
        const active =
          props.variant === "side" ? "sideNavItemActive" : "navItemActive";
        return isActive ? `${base} ${active}` : base;
      }}
    >
      <span className="navIcon" aria-hidden="true">
        {props.icon}
      </span>
      <span className="navLabel">{props.label}</span>
    </NavLink>
  );
}

export function AppShell(props: { title?: string; children: React.ReactNode }) {
  const apiBase = useSessionStore((s) => s.apiBase);
  const token = useSessionStore((s) => s.token);
  const isExpired = useSessionStore((s) => s.isTokenExpired);

  return (
    <div className="appShell">
      <header className="topbar">
        <div className="topbarLeft">
          <div className="brand">
            <span className="brandMark" aria-hidden="true">
              <svg
                className="brandLogo"
                viewBox="0 0 24 24"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
              >
                <circle
                  cx="12"
                  cy="12"
                  r="8.5"
                  stroke="currentColor"
                  strokeWidth="1.6"
                />
                <circle
                  cx="12"
                  cy="12"
                  r="4.4"
                  stroke="currentColor"
                  strokeWidth="1.6"
                  opacity="0.9"
                />
                <path
                  d="M12 6.6 L14.6 12 L12 17.4 L9.4 12 Z"
                  fill="currentColor"
                  opacity="0.95"
                />
                <path
                  d="M12 6.6 L13.4 9.6"
                  stroke="rgba(0,0,0,0.18)"
                  strokeWidth="1.2"
                  strokeLinecap="round"
                />
              </svg>
            </span>
            <span className="brandName">MealPilot</span>
          </div>
          {props.title ? <div className="pageTitle">{props.title}</div> : null}
        </div>
        <div className="topbarRight">
          <span className="pill">API: {apiBase}</span>
          <span className="pill">
            JWT: {token ? tokenPreview(token) : "none"}
            {isExpired ? " (expired)" : ""}
          </span>
        </div>
      </header>

      <div className="shellBody">
        <nav className="sideNav" aria-label="Primary">
          <div className="sideNavInner">
            <div className="sideNavTitle">Navigate</div>
            <NavItem variant="side" to="/" label="Home" icon="⌂" />
            <NavItem variant="side" to="/decide" label="Decide" icon="✦" />
            <NavItem variant="side" to="/items" label="Items" icon="⧉" />
            <NavItem variant="side" to="/history" label="History" icon="↺" />
            <NavItem
              variant="side"
              to="/preferences"
              label="Preferences"
              icon="⚑"
            />
            <NavItem variant="side" to="/settings" label="Settings" icon="⚙" />
          </div>
        </nav>

        <main className="page">{props.children}</main>
      </div>

      <nav className="bottomNav" aria-label="Primary">
        <NavItem variant="bottom" to="/" label="Home" icon="⌂" />
        <NavItem variant="bottom" to="/decide" label="Decide" icon="✦" />
        <NavItem variant="bottom" to="/items" label="Items" icon="⧉" />
        <NavItem variant="bottom" to="/history" label="History" icon="↺" />
        <NavItem variant="bottom" to="/preferences" label="Prefs" icon="⚑" />
        <NavItem variant="bottom" to="/settings" label="Settings" icon="⚙" />
      </nav>
    </div>
  );
}
