import { useEffect, useState } from "react";
import { onSyncStateChange } from "../api/client";

/**
 * Module 14: shows real connectivity/sync state, never a faked "synced" when data has not
 * actually round-tripped. Backed by navigator.onLine plus the api client's request lifecycle.
 */
export function SyncBadge() {
  const [state, setState] = useState<"synced" | "syncing" | "offline">(
    navigator.onLine ? "synced" : "offline",
  );

  useEffect(() => {
    const unsubscribe = onSyncStateChange(setState);
    const goOffline = () => setState("offline");
    const goOnline = () => setState("synced");
    window.addEventListener("offline", goOffline);
    window.addEventListener("online", goOnline);
    return () => {
      unsubscribe();
      window.removeEventListener("offline", goOffline);
      window.removeEventListener("online", goOnline);
    };
  }, []);

  const meta = {
    synced: { icon: "🟢", label: "Synced" },
    syncing: { icon: "🔄", label: "Syncing" },
    offline: { icon: "🟠", label: "Offline" },
  }[state];

  return (
    <span className="sync-badge">
      <span aria-hidden="true">{meta.icon}</span>
      {meta.label}
    </span>
  );
}
