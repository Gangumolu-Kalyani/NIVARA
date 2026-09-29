import { useState, type FormEvent } from "react";
import type { DayOfWeekCode, ReminderCategory, ReminderRepeatType, ReminderResponse } from "../types/api";

const CATEGORIES: Array<{ value: ReminderCategory; label: string; icon: string }> = [
  { value: "MEDICINE", label: "Medicine", icon: "💊" },
  { value: "HYDRATION", label: "Hydration", icon: "💧" },
  { value: "APPOINTMENT", label: "Appointment", icon: "📅" },
  { value: "MOVEMENT", label: "Movement", icon: "🚶" },
  { value: "COGNITIVE_ACTIVITY", label: "Cognitive Activity", icon: "🧠" },
  { value: "MEAL", label: "Meal", icon: "🍽️" },
];
const DAYS: DayOfWeekCode[] = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"];

export interface ReminderFormValue {
  category: ReminderCategory;
  title: string;
  instructions: string;
  scheduledTime: string;
  repeatType: ReminderRepeatType;
  repeatDays: DayOfWeekCode[];
  oneOffDate: string;
  escalateAfterMissed: number;
  active: boolean;
}

function fromReminder(reminder?: ReminderResponse): ReminderFormValue {
  return {
    category: reminder?.category ?? "MEDICINE",
    title: reminder?.title ?? "",
    instructions: reminder?.instructions ?? "",
    scheduledTime: reminder?.scheduledTime?.slice(0, 5) ?? "08:00",
    repeatType: reminder?.repeatType ?? "DAILY",
    repeatDays: reminder?.repeatDays ?? [],
    oneOffDate: reminder?.oneOffDate ?? "",
    escalateAfterMissed: reminder?.escalateAfterMissed ?? 2,
    active: reminder?.active ?? true,
  };
}

interface Props {
  reminder?: ReminderResponse;
  onCancel: () => void;
  onSubmit: (value: ReminderFormValue) => Promise<void>;
}

/** Module 6: create/edit a Daily Assistance reminder. */
export function ReminderForm({ reminder, onCancel, onSubmit }: Props) {
  const [value, setValue] = useState<ReminderFormValue>(() => fromReminder(reminder));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function toggleDay(day: DayOfWeekCode) {
    setValue((v) => ({
      ...v,
      repeatDays: v.repeatDays.includes(day) ? v.repeatDays.filter((d) => d !== day) : [...v.repeatDays, day],
    }));
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (value.repeatType === "WEEKLY" && value.repeatDays.length === 0) {
      setError("Choose at least one day for a weekly reminder.");
      return;
    }
    if (value.repeatType === "ONCE" && !value.oneOffDate) {
      setError("Choose a date for a one-time reminder.");
      return;
    }
    setBusy(true);
    try {
      await onSubmit(value);
    } catch {
      setError("Could not save this reminder. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="card" onSubmit={handleSubmit}>
      <h2 className="section-title" style={{ marginTop: 0 }}>
        {reminder ? "Edit reminder" : "New reminder"}
      </h2>

      <div className="field">
        <label>Category</label>
        <div className="chip-group">
          {CATEGORIES.map((c) => (
            <button
              key={c.value}
              type="button"
              className={`chip ${value.category === c.value ? "selected" : ""}`}
              onClick={() => setValue((v) => ({ ...v, category: c.value }))}
            >
              <span aria-hidden="true">{c.icon}</span> {c.label}
            </button>
          ))}
        </div>
      </div>

      <div className="field">
        <label htmlFor="title">Title</label>
        <input
          id="title"
          value={value.title}
          onChange={(e) => setValue((v) => ({ ...v, title: e.target.value }))}
          placeholder="Evening Medicine"
          required
        />
      </div>

      <div className="field">
        <label htmlFor="instructions">Instructions (optional)</label>
        <textarea
          id="instructions"
          value={value.instructions}
          onChange={(e) => setValue((v) => ({ ...v, instructions: e.target.value }))}
        />
      </div>

      <div className="field">
        <label htmlFor="time">Time</label>
        <input
          id="time"
          type="time"
          value={value.scheduledTime}
          onChange={(e) => setValue((v) => ({ ...v, scheduledTime: e.target.value }))}
          required
        />
      </div>

      <div className="field">
        <label>Repeat</label>
        <div className="chip-group">
          {(["DAILY", "WEEKLY", "ONCE"] as ReminderRepeatType[]).map((r) => (
            <button
              key={r}
              type="button"
              className={`chip ${value.repeatType === r ? "selected" : ""}`}
              onClick={() => setValue((v) => ({ ...v, repeatType: r }))}
            >
              {r === "DAILY" ? "Every day" : r === "WEEKLY" ? "Some days" : "One time"}
            </button>
          ))}
        </div>
      </div>

      {value.repeatType === "WEEKLY" && (
        <div className="field">
          <label>Days</label>
          <div className="chip-group">
            {DAYS.map((day) => (
              <button
                key={day}
                type="button"
                className={`chip ${value.repeatDays.includes(day) ? "selected" : ""}`}
                onClick={() => toggleDay(day)}
              >
                {day}
              </button>
            ))}
          </div>
        </div>
      )}

      {value.repeatType === "ONCE" && (
        <div className="field">
          <label htmlFor="oneOffDate">Date</label>
          <input
            id="oneOffDate"
            type="date"
            value={value.oneOffDate}
            onChange={(e) => setValue((v) => ({ ...v, oneOffDate: e.target.value }))}
            required
          />
        </div>
      )}

      <div className="field">
        <label htmlFor="escalate">Escalate after unanswered reminders</label>
        <input
          id="escalate"
          type="number"
          min={1}
          max={10}
          value={value.escalateAfterMissed}
          onChange={(e) => setValue((v) => ({ ...v, escalateAfterMissed: Number(e.target.value) }))}
        />
      </div>

      {reminder && (
        <div className="field">
          <label>
            <input
              type="checkbox"
              checked={value.active}
              onChange={(e) => setValue((v) => ({ ...v, active: e.target.checked }))}
              style={{ marginRight: 8, minHeight: "auto" }}
            />
            Reminder is active
          </label>
        </div>
      )}

      {error && (
        <p role="alert" className="status-pill warning">
          {error}
        </p>
      )}

      <div className="btn-row" style={{ marginTop: "var(--space-4)" }}>
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy ? "Saving…" : "Save reminder"}
        </button>
        <button type="button" className="btn" onClick={onCancel} disabled={busy}>
          Cancel
        </button>
      </div>
    </form>
  );
}
