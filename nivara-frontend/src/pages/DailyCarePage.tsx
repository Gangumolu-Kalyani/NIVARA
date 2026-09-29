import { useEffect, useState } from "react";
import { DashboardApi, RemindersApi } from "../api/endpoints";
import { usePatients } from "../auth/PatientContext";
import { PatientSelector } from "../components/PatientSelector";
import { ReminderForm, type ReminderFormValue } from "../components/ReminderForm";
import type { DailySummaryResponse, ReminderUpdateRequest, ReminderResponse } from "../types/api";

const CATEGORY_ICON: Record<string, string> = {
  MEDICINE: "💊",
  HYDRATION: "💧",
  APPOINTMENT: "📅",
  MOVEMENT: "🚶",
  COGNITIVE_ACTIVITY: "🧠",
  MEAL: "🍽️",
};

const RHYTHM_META: Record<string, { icon: string; label: string }> = {
  STRONG: { icon: "🟢", label: "Strong" },
  NEEDS_PROMPTING: { icon: "🟡", label: "Needs prompting" },
  LOW: { icon: "🔴", label: "Low" },
  NO_DATA: { icon: "⚪", label: "No data yet" },
};

function toRequest(value: ReminderFormValue): ReminderUpdateRequest {
  return {
    category: value.category,
    title: value.title,
    instructions: value.instructions || null,
    scheduledTime: `${value.scheduledTime}:00`,
    repeatType: value.repeatType,
    repeatDays: value.repeatType === "WEEKLY" ? value.repeatDays : null,
    oneOffDate: value.repeatType === "ONCE" ? value.oneOffDate : null,
    escalateAfterMissed: value.escalateAfterMissed,
    active: value.active,
  };
}

export function DailyCarePage() {
  const { selected } = usePatients();
  const [reminders, setReminders] = useState<ReminderResponse[]>([]);
  const [dailySummary, setDailySummary] = useState<DailySummaryResponse | null>(null);
  const [editing, setEditing] = useState<ReminderResponse | "new" | null>(null);
  const [loading, setLoading] = useState(true);

  function reload() {
    if (!selected) return;
    setLoading(true);
    Promise.all([RemindersApi.list(selected.uuid), DashboardApi.dailySummary(selected.uuid)])
      .then(([reminderList, summary]) => {
        setReminders(reminderList);
        setDailySummary(summary);
      })
      .finally(() => setLoading(false));
  }

  useEffect(reload, [selected]);

  async function handleSubmit(value: ReminderFormValue) {
    if (!selected) return;
    if (editing === "new") {
      await RemindersApi.create(selected.uuid, toRequest(value));
    } else if (editing) {
      await RemindersApi.update(editing.uuid, toRequest(value));
    }
    setEditing(null);
    reload();
  }

  async function toggleActive(reminder: ReminderResponse) {
    await RemindersApi.update(reminder.uuid, {
      category: reminder.category,
      title: reminder.title,
      instructions: reminder.instructions,
      scheduledTime: reminder.scheduledTime,
      repeatType: reminder.repeatType,
      repeatDays: reminder.repeatDays,
      oneOffDate: reminder.oneOffDate,
      escalateAfterMissed: reminder.escalateAfterMissed,
      active: !reminder.active,
    });
    reload();
  }

  if (!selected) {
    return (
      <div className="page">
        <p className="empty-state">Add a patient to manage Daily Assistance.</p>
      </div>
    );
  }

  if (editing) {
    return (
      <div className="page">
        <ReminderForm
          reminder={editing === "new" ? undefined : editing}
          onCancel={() => setEditing(null)}
          onSubmit={handleSubmit}
        />
      </div>
    );
  }

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="greeting" style={{ fontSize: "var(--font-size-xl)" }}>
            Daily Assistance
          </h1>
          <PatientSelector />
        </div>
      </div>

      {loading && <p className="subtitle">Loading…</p>}

      {dailySummary && (
        <>
          <h2 className="section-title" style={{ marginTop: 0 }}>
            Today's Summary
          </h2>
          <div className="card">
            {dailySummary.summaryLines.map((line, i) => (
              <p key={i} style={{ margin: "0 0 var(--space-2)" }}>
                {line}
              </p>
            ))}
          </div>

          <h2 className="section-title">Care Rhythm</h2>
          <div className="card">
            <div className="stat-grid" style={{ gridTemplateColumns: "repeat(3, 1fr)" }}>
              {(["morning", "afternoon", "evening"] as const).map((slot) => {
                const level = dailySummary.careRhythm[slot];
                const meta = RHYTHM_META[level];
                return (
                  <div key={slot} style={{ textAlign: "center" }}>
                    <div className="stat-label" style={{ textTransform: "capitalize" }}>
                      {slot}
                    </div>
                    <div style={{ fontSize: 20 }}>
                      {meta.icon} {meta.label}
                    </div>
                  </div>
                );
              })}
            </div>
            {dailySummary.patterns.length > 0 && (
              <div style={{ marginTop: "var(--space-4)" }}>
                <strong style={{ fontSize: "var(--font-size-sm)" }}>Patterns</strong>
                {dailySummary.patterns.map((pattern, i) => (
                  <p key={i} className="subtitle" style={{ margin: "4px 0" }}>
                    {pattern}
                  </p>
                ))}
              </div>
            )}
          </div>
        </>
      )}

      <div className="page-header" style={{ marginTop: "var(--space-6)" }}>
        <h2 className="section-title" style={{ margin: 0 }}>
          Reminders
        </h2>
        <button type="button" className="btn btn-primary" onClick={() => setEditing("new")}>
          + New reminder
        </button>
      </div>

      {reminders.length === 0 ? (
        <p className="empty-state">No reminders yet. Add the first one above.</p>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)" }}>
          {reminders.map((reminder) => (
            <div key={reminder.uuid} className="card">
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                <div>
                  <div className="timeline-title">
                    <span aria-hidden="true">{CATEGORY_ICON[reminder.category]}</span>
                    {reminder.title}
                  </div>
                  <p className="subtitle" style={{ margin: 0 }}>
                    {reminder.scheduledTime.slice(0, 5)} ·{" "}
                    {reminder.repeatType === "DAILY"
                      ? "Every day"
                      : reminder.repeatType === "WEEKLY"
                        ? reminder.repeatDays.join(", ")
                        : reminder.oneOffDate}
                  </p>
                  {reminder.instructions && <p style={{ margin: "var(--space-2) 0 0" }}>{reminder.instructions}</p>}
                </div>
                <span className={`status-pill ${reminder.active ? "success" : "neutral"}`}>
                  {reminder.active ? "✓ Active" : "Paused"}
                </span>
              </div>
              <div className="btn-row" style={{ marginTop: "var(--space-4)" }}>
                <button type="button" className="btn" onClick={() => setEditing(reminder)}>
                  Edit
                </button>
                <button type="button" className="btn" onClick={() => toggleActive(reminder)}>
                  {reminder.active ? "Disable" : "Enable"}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
