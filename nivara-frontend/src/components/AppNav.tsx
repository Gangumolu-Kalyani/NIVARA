import { NavLink } from "react-router-dom";
import { SyncBadge } from "./SyncBadge";
import { useAuth } from "../auth/AuthContext";

const NAV_ITEMS = [
  { to: "/overview", icon: "🏠", label: "Overview" },
  { to: "/progress", icon: "📊", label: "Progress" },
  { to: "/daily-care", icon: "🧭", label: "Daily Care" },
  { to: "/alerts", icon: "🔔", label: "Alerts" },
  { to: "/patient", icon: "👤", label: "Patient" },
];

function navLinkClass({ isActive }: { isActive: boolean }) {
  return `nav-item${isActive ? " active" : ""}`;
}

/** Bottom tab bar on mobile/tablet (Module 15); becomes a compact top bar at desktop widths. */
export function AppNav() {
  const { account, logout } = useAuth();

  return (
    <>
      <header className="top-bar">
        <strong>NIVARA</strong>
        <nav aria-label="Main">
          {NAV_ITEMS.map((item) => (
            <NavLink key={item.to} to={item.to} className={navLinkClass}>
              <span aria-hidden="true">{item.icon}</span> {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="btn-row" style={{ alignItems: "center" }}>
          <SyncBadge />
          {account && (
            <button type="button" className="btn" onClick={logout}>
              Log out
            </button>
          )}
        </div>
      </header>

      <nav className="bottom-nav" aria-label="Main">
        {NAV_ITEMS.map((item) => (
          <NavLink key={item.to} to={item.to} className={navLinkClass}>
            <span className="nav-icon" aria-hidden="true">
              {item.icon}
            </span>
            {item.label}
          </NavLink>
        ))}
      </nav>
    </>
  );
}
