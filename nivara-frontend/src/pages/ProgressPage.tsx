import { useEffect, useState } from "react";
import { DashboardApi } from "../api/endpoints";
import { usePatients } from "../auth/PatientContext";
import { AccuracyTrendChart, CountBarChart, DomainBarChart } from "../components/charts";
import { PatientSelector } from "../components/PatientSelector";
import type { GameResultSummaryResponse, ProgressResponse } from "../types/api";

const DIFFICULTY_LABEL: Record<number, string> = { 1: "Easiest", 2: "Easy", 3: "Medium", 4: "Hard", 5: "Hardest" };
const DOMAIN_LABEL: Record<string, string> = {
  MEMORY: "Memory",
  ATTENTION: "Attention",
  LANGUAGE: "Language",
  EXECUTIVE_FUNCTION: "Sequence",
  ORIENTATION: "Orientation",
  VISUOSPATIAL: "Recognition",
  PROCESSING_SPEED: "Speed",
};
const TREND_LABEL: Record<string, string> = {
  UP: "↑ Improving",
  DOWN: "↓ Needs practice",
  STABLE: "→ Steady",
  INSUFFICIENT_DATA: "",
};

function GameActivityRow({ activity }: { activity: GameResultSummaryResponse }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="card" style={{ cursor: "pointer" }} onClick={() => setOpen(!open)}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <div>
          <strong>{activity.game.name}</strong>
          <p className="subtitle" style={{ margin: 0 }}>
            {new Date(activity.startedAt).toLocaleString([], { dateStyle: "medium", timeStyle: "short" })}
          </p>
        </div>
        <div style={{ textAlign: "right" }}>
          <div>{activity.accuracy !== null ? `${activity.accuracy}%` : "—"}</div>
          <span className={`status-pill ${activity.status === "COMPLETED" ? "success" : "warning"}`}>
            {activity.status === "COMPLETED" ? "✓ Completed" : "⚠ Abandoned"}
          </span>
        </div>
      </div>
      {open && (
        <dl style={{ marginTop: "var(--space-4)", display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, fontSize: "var(--font-size-sm)" }}>
          <dt className="subtitle">Difficulty</dt>
          <dd style={{ margin: 0 }}>{DIFFICULTY_LABEL[activity.difficultyLevel] ?? activity.difficultyLevel}</dd>
          <dt className="subtitle">Started</dt>
          <dd style={{ margin: 0 }}>{new Date(activity.startedAt).toLocaleTimeString()}</dd>
          <dt className="subtitle">Completed</dt>
          <dd style={{ margin: 0 }}>
            {activity.completedAt ? new Date(activity.completedAt).toLocaleTimeString() : "—"}
          </dd>
          <dt className="subtitle">Questions</dt>
          <dd style={{ margin: 0 }}>
            {activity.correctAnswers} / {activity.totalQuestions} correct
          </dd>
          <dt className="subtitle">Hints used</dt>
          <dd style={{ margin: 0 }}>{activity.hintsUsed}</dd>
          <dt className="subtitle">Activity area</dt>
          <dd style={{ margin: 0 }}>{DOMAIN_LABEL[activity.cognitiveDomain] ?? activity.cognitiveDomain}</dd>
        </dl>
      )}
    </div>
  );
}

export function ProgressPage() {
  const { selected } = usePatients();
  const [range, setRange] = useState<7 | 30>(7);
  const [progress, setProgress] = useState<ProgressResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!selected) return;
    setLoading(true);
    DashboardApi.progress(selected.uuid, range)
      .then(setProgress)
      .finally(() => setLoading(false));
  }, [selected, range]);

  if (!selected) {
    return (
      <div className="page">
        <p className="empty-state">Add a patient to see their progress.</p>
      </div>
    );
  }

  const domainData = progress
    ? Object.entries(progress.domainAccuracy).map(([domain, value]) => ({
        label: DOMAIN_LABEL[domain] ?? domain,
        value: Math.round(value),
      }))
    : [];
  const difficultyData = progress
    ? Object.entries(progress.difficultyDistribution)
        .sort(([a], [b]) => Number(a) - Number(b))
        .map(([level, count]) => ({ label: DIFFICULTY_LABEL[Number(level)] ?? `Level ${level}`, value: count }))
    : [];

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="greeting" style={{ fontSize: "var(--font-size-xl)" }}>
            Activity Performance
          </h1>
          <PatientSelector />
        </div>
      </div>

      <div className="chip-group" style={{ marginBottom: "var(--space-5)" }}>
        <button type="button" className={`chip ${range === 7 ? "selected" : ""}`} onClick={() => setRange(7)}>
          7 Days
        </button>
        <button type="button" className={`chip ${range === 30 ? "selected" : ""}`} onClick={() => setRange(30)}>
          30 Days
        </button>
      </div>

      {loading && <p className="subtitle">Loading…</p>}

      {progress && (
        <>
          <div className="stat-grid">
            <StatBlock label="Games completed" value={String(progress.gamesCompleted)} />
            <StatBlock label="Average accuracy" value={progress.averageAccuracy !== null ? `${progress.averageAccuracy}%` : "—"} />
            <StatBlock label="Games abandoned" value={String(progress.gamesAbandoned)} />
            <StatBlock
              label="Avg. response time"
              value={progress.averageReactionTimeMs !== null ? `${(progress.averageReactionTimeMs / 1000).toFixed(1)}s` : "—"}
            />
          </div>

          {progress.overallAccuracyTrend !== "INSUFFICIENT_DATA" && (
            <p className="subtitle" style={{ marginTop: "var(--space-3)" }}>
              {TREND_LABEL[progress.overallAccuracyTrend]}
            </p>
          )}

          <h2 className="section-title">Performance Trends</h2>
          <div className="card">
            <AccuracyTrendChart points={progress.accuracyTrend} />
          </div>

          <h2 className="section-title">Activity Area Performance</h2>
          <div className="card">
            <DomainBarChart data={domainData} />
          </div>

          <h2 className="section-title">Difficulty Distribution</h2>
          <div className="card">
            <CountBarChart data={difficultyData} />
          </div>

          <h2 className="section-title">Recent Cognitive Activities</h2>
          {progress.recentActivities.length === 0 ? (
            <p className="empty-state">No activities played in this window yet.</p>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)" }}>
              {progress.recentActivities.map((activity) => (
                <GameActivityRow key={activity.uuid} activity={activity} />
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}

function StatBlock({ label, value }: { label: string; value: string }) {
  return (
    <div className="stat-card">
      <span className="stat-label">{label}</span>
      <span className="stat-value">{value}</span>
    </div>
  );
}
