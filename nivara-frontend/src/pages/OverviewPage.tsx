import { useEffect, useState, type FormEvent } from "react";
import { DashboardApi, PatientsApi, RemindersApi } from "../api/endpoints";
import { useAuth } from "../auth/AuthContext";
import { usePatients } from "../auth/PatientContext";
import { PatientSelector } from "../components/PatientSelector";
import { StatCard, formatCard } from "../components/StatCard";
import { StatusPill } from "../components/StatusPill";
import type { DashboardSummaryResponse, ReminderOccurrenceResponse } from "../types/api";

const CATEGORY_ICON: Record<string, string> = {
  MEDICINE: "💊",
  HYDRATION: "💧",
  APPOINTMENT: "📅",
  MOVEMENT: "🚶",
  COGNITIVE_ACTIVITY: "🧠",
  MEAL: "🍽️",
};

function greetingWord(): string {
  const hour = new Date().getHours();
  if (hour < 12) return "Good morning";
  if (hour < 17) return "Good afternoon";
  return "Good evening";
}

// Local calendar date, not toISOString(): that is the UTC date, which in India is still
// yesterday until 05:30.
function todayIso(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${day}`;
}

function AddFirstPatient() {
  const { reload } = usePatients();
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!name.trim()) return;
    setBusy(true);
    try {
      await PatientsApi.create(name.trim());
      reload();
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="card" onSubmit={submit}>
      <h2 className="section-title" style={{ marginTop: 0 }}>
        Add your first patient
      </h2>
      <div className="field">
        <label htmlFor="patientName">Patient's full name</label>
        <input id="patientName" value={name} onChange={(e) => setName(e.target.value)} placeholder="Lakshmi Rao" required />
      </div>
      <button type="submit" className="btn btn-primary" disabled={busy}>
        {busy ? "Adding…" : "Add patient"}
      </button>
    </form>
  );
}

export function OverviewPage() {
  const { account } = useAuth();
  const { selected, patients, loading: patientsLoading } = usePatients();
  const [summary, setSummary] = useState<DashboardSummaryResponse | null>(null);
  const [timeline, setTimeline] = useState<ReminderOccurrenceResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!selected) return;
    setLoading(true);
    setError(null);
    Promise.all([DashboardApi.summary(selected.uuid), RemindersApi.dailyCare(selected.uuid, todayIso())])
      .then(([summaryResponse, timelineResponse]) => {
        setSummary(summaryResponse);
        setTimeline(timelineResponse);
      })
      .catch(() => setError("Could not load today's dashboard."))
      .finally(() => setLoading(false));
  }, [selected]);

  if (!selected) {
    return (
      <div className="page">
        {patientsLoading ? (
          <p className="subtitle">Loading…</p>
        ) : patients.length === 0 ? (
          <AddFirstPatient />
        ) : (
          <p className="empty-state">Select a patient to see their dashboard.</p>
        )}
      </div>
    );
  }

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="greeting">
            {greetingWord()}, {account?.fullName?.split(" ")[0] ?? "there"}
          </h1>
          <PatientSelector />
        </div>
      </div>

      {loading && <p className="subtitle">Loading…</p>}
      {error && <StatusPill status="MISSED" />}

      {summary && (
        <>
          <div
            className={`status-pill ${summary.careStatus === "ON_TRACK" ? "success" : "warning"}`}
            style={{ marginBottom: "var(--space-4)" }}
          >
            <span aria-hidden="true">{summary.careStatus === "ON_TRACK" ? "✓" : "⚠"}</span>
            {summary.careStatus === "ON_TRACK" ? "Today's care is on track" : "Today needs attention"}
          </div>

          <div className="stat-grid">
            <StatCard
              icon="🧠"
              label="Cognitive Activity"
              {...formatCard(summary.dailyActivity.cognitiveActivity.completed, summary.dailyActivity.cognitiveActivity.total)}
            />
            <StatCard
              icon="💊"
              label="Medicine"
              {...formatCard(summary.dailyActivity.medicine.completed, summary.dailyActivity.medicine.total)}
            />
            <StatCard
              icon="💧"
              label="Hydration"
              {...formatCard(summary.dailyActivity.hydration.completed, summary.dailyActivity.hydration.total)}
            />
            <StatCard
              icon="🚶"
              label="Movement"
              {...formatCard(summary.dailyActivity.movement.completed, summary.dailyActivity.movement.total)}
            />
          </div>

          {summary.openAlerts.length > 0 && (
            <>
              <h2 className="section-title">Needs attention</h2>
              <div className="card">
                {summary.openAlerts.slice(0, 3).map((alert) => (
                  <p key={alert.uuid} style={{ margin: "0 0 var(--space-2)" }}>
                    🔴 {alert.title}
                  </p>
                ))}
              </div>
            </>
          )}
        </>
      )}

      <h2 className="section-title">Today's Care Timeline</h2>
      {timeline.length === 0 ? (
        <p className="empty-state">No reminders scheduled for today yet.</p>
      ) : (
        <ol className="timeline">
          {timeline.map((item) => (
            <li key={item.uuid} className="timeline-item">
              <span className="timeline-time">
                {new Date(item.scheduledAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
              </span>
              <div className="card timeline-card">
                <div className="timeline-title">
                  <span aria-hidden="true">{CATEGORY_ICON[item.category] ?? "📌"}</span>
                  {item.reminderTitle}
                </div>
                <StatusPill status={item.responseStatus} />
              </div>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
