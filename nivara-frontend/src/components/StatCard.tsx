interface StatCardProps {
  icon: string;
  label: string;
  value: string;
  done: boolean;
}

/** One of the four dashboard summary cards (Module 1). Always icon + text, never color alone. */
export function StatCard({ icon, label, value, done }: StatCardProps) {
  return (
    <div className="stat-card">
      <span className="stat-icon" aria-hidden="true">
        {icon}
      </span>
      <span className="stat-label">{label}</span>
      <span className="stat-value">
        {done ? "✓ " : ""}
        {value}
      </span>
    </div>
  );
}

export function formatCard(completed: number, total: number): { value: string; done: boolean } {
  if (total === 0) {
    return { value: "No reminders set", done: false };
  }
  return { value: `${completed} / ${total}`, done: completed === total };
}
