import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { AlertsApi } from "../api/endpoints";
import { usePatients } from "../auth/PatientContext";
import { PatientSelector } from "../components/PatientSelector";
import type { AlertResponse } from "../types/api";

type Tab = "attention" | "followUp" | "completed";

const SEVERITY_ICON: Record<string, string> = { HIGH: "🔴", MEDIUM: "🟠", LOW: "🟢" };

export function AlertsPage() {
  const { selected } = usePatients();
  const navigate = useNavigate();
  const [alerts, setAlerts] = useState<AlertResponse[]>([]);
  const [tab, setTab] = useState<Tab>("attention");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!selected) return;
    setLoading(true);
    AlertsApi.list(selected.uuid)
      .then(setAlerts)
      .finally(() => setLoading(false));
  }, [selected]);

  if (!selected) {
    return (
      <div className="page">
        <p className="empty-state">Add a patient to see their alerts.</p>
      </div>
    );
  }

  const filtered = alerts.filter((a) => {
    if (tab === "completed") return a.status !== "OPEN";
    if (tab === "attention") return a.status === "OPEN" && a.severity === "HIGH";
    return a.status === "OPEN" && a.severity !== "HIGH";
  });

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="greeting" style={{ fontSize: "var(--font-size-xl)" }}>
            Alerts
          </h1>
          <PatientSelector />
        </div>
      </div>

      <div className="chip-group" style={{ marginBottom: "var(--space-5)" }}>
        <button type="button" className={`chip ${tab === "attention" ? "selected" : ""}`} onClick={() => setTab("attention")}>
          Needs Attention
        </button>
        <button type="button" className={`chip ${tab === "followUp" ? "selected" : ""}`} onClick={() => setTab("followUp")}>
          Follow Up
        </button>
        <button type="button" className={`chip ${tab === "completed" ? "selected" : ""}`} onClick={() => setTab("completed")}>
          Completed
        </button>
      </div>

      {loading && <p className="subtitle">Loading…</p>}

      {filtered.length === 0 ? (
        <p className="empty-state">Nothing here right now.</p>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)" }}>
          {filtered.map((alert) => (
            <button
              key={alert.uuid}
              type="button"
              className="card"
              style={{ textAlign: "left", cursor: "pointer", width: "100%" }}
              onClick={() => navigate(`/alerts/${alert.uuid}`)}
            >
              <div style={{ display: "flex", justifyContent: "space-between", gap: "var(--space-3)" }}>
                <div>
                  <div className="timeline-title">
                    <span aria-hidden="true">{SEVERITY_ICON[alert.severity]}</span>
                    {alert.title}
                  </div>
                  <p className="subtitle" style={{ margin: 0 }}>
                    {new Date(alert.createdAt).toLocaleString([], { dateStyle: "medium", timeStyle: "short" })}
                  </p>
                  <p style={{ margin: "var(--space-2) 0 0" }}>{alert.message}</p>
                </div>
              </div>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
