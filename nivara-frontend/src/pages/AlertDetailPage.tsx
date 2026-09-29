import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { AlertsApi } from "../api/endpoints";
import type { AlertResponse } from "../types/api";

export function AlertDetailPage() {
  const { uuid } = useParams<{ uuid: string }>();
  const navigate = useNavigate();
  const [alert, setAlert] = useState<AlertResponse | null>(null);
  const [busy, setBusy] = useState(false);

  function reload() {
    if (!uuid) return;
    AlertsApi.get(uuid).then(setAlert);
  }

  useEffect(reload, [uuid]);

  async function act(action: "resolve" | "dismiss") {
    if (!uuid) return;
    setBusy(true);
    try {
      const updated = action === "resolve" ? await AlertsApi.resolve(uuid) : await AlertsApi.dismiss(uuid);
      setAlert(updated);
    } finally {
      setBusy(false);
    }
  }

  if (!alert) {
    return (
      <div className="page">
        <p className="subtitle">Loading…</p>
      </div>
    );
  }

  return (
    <div className="page">
      <button type="button" className="btn" onClick={() => navigate(-1)} style={{ marginBottom: "var(--space-4)" }}>
        ← Back
      </button>

      <h1 className="greeting" style={{ fontSize: "var(--font-size-xl)" }}>
        Alert Details
      </h1>

      <div className="card">
        <dl style={{ display: "grid", gridTemplateColumns: "auto 1fr", gap: "var(--space-2) var(--space-4)" }}>
          <dt className="subtitle">Type</dt>
          <dd style={{ margin: 0 }}>{alert.category}</dd>
          <dt className="subtitle">Severity</dt>
          <dd style={{ margin: 0 }}>{alert.severity}</dd>
          <dt className="subtitle">Raised</dt>
          <dd style={{ margin: 0 }}>{new Date(alert.createdAt).toLocaleString()}</dd>
          <dt className="subtitle">Status</dt>
          <dd style={{ margin: 0 }}>
            <span
              className={`status-pill ${alert.status === "OPEN" ? "danger" : alert.status === "RESOLVED" ? "success" : "neutral"}`}
            >
              {alert.status}
            </span>
          </dd>
          {alert.resolvedAt && (
            <>
              <dt className="subtitle">Closed</dt>
              <dd style={{ margin: 0 }}>
                {new Date(alert.resolvedAt).toLocaleString()}
                {alert.resolvedByName ? ` by ${alert.resolvedByName}` : ""}
              </dd>
            </>
          )}
        </dl>

        <p style={{ marginTop: "var(--space-4)" }}>{alert.message}</p>

        {alert.status === "OPEN" && (
          <div className="btn-row" style={{ marginTop: "var(--space-5)" }}>
            <button
              type="button"
              className="btn"
              title="No phone number is on file for this patient yet — this action is a placeholder."
              onClick={() => window.alert("Calling the patient is not wired up yet: no phone number is on file.")}
            >
              📞 Call Patient
            </button>
            <button type="button" className="btn btn-primary" disabled={busy} onClick={() => act("resolve")}>
              Mark Resolved
            </button>
            <button type="button" className="btn" disabled={busy} onClick={() => act("dismiss")}>
              Dismiss
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
