import type { ReminderResponseStatus } from "../types/api";

const STATUS_META: Record<ReminderResponseStatus, { icon: string; label: string; tone: string }> = {
  PENDING: { icon: "⏳", label: "Pending", tone: "neutral" },
  SENT: { icon: "\u{1F4E4}", label: "Sent", tone: "neutral" },
  SEEN: { icon: "\u{1F441}️", label: "Seen", tone: "warning" },
  COMPLETED: { icon: "✓", label: "Confirmed", tone: "success" },
  MISSED: { icon: "⚠", label: "Missed", tone: "danger" },
  ESCALATED: { icon: "\u{1F6A8}", label: "Escalated", tone: "danger" },
};

/** Icon + text status, never color alone (accessibility requirement). */
export function StatusPill({ status }: { status: ReminderResponseStatus }) {
  const meta = STATUS_META[status];
  return (
    <span className={`status-pill ${meta.tone}`}>
      <span aria-hidden="true">{meta.icon}</span>
      {meta.label}
    </span>
  );
}
