import type { DailyAccuracyPoint } from "../types/api";

/**
 * Deliberately simple charts (Module 5): one hue per chart (sequential, magnitude-only), one
 * axis, thin marks, direct value labels instead of a legend — a single series names itself in
 * the section title, so no legend box is needed. Every value drawn here is also present as
 * plain text nearby in the page, which doubles as the "table view" fallback.
 */

const CHART_COLOR = "var(--color-indigo)";

export function AccuracyTrendChart({ points }: { points: DailyAccuracyPoint[] }) {
  const withData = points.filter((p) => p.averageAccuracy !== null);
  if (withData.length < 2) {
    return <p className="subtitle">Not enough days played yet to show a trend line.</p>;
  }

  const width = 320;
  const height = 120;
  const padding = 16;
  const values = withData.map((p) => p.averageAccuracy as number);
  const min = Math.min(...values, 0);
  const max = Math.max(...values, 100);
  const stepX = (width - padding * 2) / (withData.length - 1);

  const coords = withData.map((p, i) => {
    const x = padding + i * stepX;
    const y = height - padding - ((((p.averageAccuracy as number) - min) / (max - min || 1)) * (height - padding * 2));
    return { x, y, point: p };
  });
  const path = coords.map((c, i) => `${i === 0 ? "M" : "L"}${c.x.toFixed(1)},${c.y.toFixed(1)}`).join(" ");

  return (
    <svg
      viewBox={`0 0 ${width} ${height}`}
      role="img"
      aria-label={`Accuracy trend from ${withData[0].averageAccuracy}% to ${withData[withData.length - 1].averageAccuracy}%`}
      style={{ width: "100%", height: "auto" }}
    >
      <line x1={padding} y1={height - padding} x2={width - padding} y2={height - padding}
        stroke="var(--color-border)" strokeWidth={1} />
      <path d={path} fill="none" stroke={CHART_COLOR} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" />
      {coords.map((c, i) => (
        <circle key={i} cx={c.x} cy={c.y} r={3.5} fill={CHART_COLOR}>
          <title>
            {c.point.date}: {c.point.averageAccuracy}% ({c.point.gamesPlayed} game{c.point.gamesPlayed === 1 ? "" : "s"})
          </title>
        </circle>
      ))}
      <text x={padding} y={height - 2} fontSize={10} fill="var(--color-text-faint)">
        {withData[0].date}
      </text>
      <text x={width - padding} y={height - 2} fontSize={10} fill="var(--color-text-faint)" textAnchor="end">
        {withData[withData.length - 1].date}
      </text>
    </svg>
  );
}

export function DomainBarChart({ data }: { data: Array<{ label: string; value: number }> }) {
  return <BarChart data={data} formatValue={(v) => `${v}%`} />;
}

export function CountBarChart({ data }: { data: Array<{ label: string; value: number }> }) {
  return <BarChart data={data} formatValue={(v) => String(v)} />;
}

function BarChart({
  data,
  formatValue,
}: {
  data: Array<{ label: string; value: number }>;
  formatValue: (value: number) => string;
}) {
  if (data.length === 0) {
    return <p className="subtitle">No games played in this window yet.</p>;
  }
  const max = Math.max(...data.map((d) => d.value), 1);

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
      {data.map((d) => (
        <div key={d.label}>
          <div style={{ display: "flex", justifyContent: "space-between", fontSize: "var(--font-size-sm)", marginBottom: 4 }}>
            <span>{d.label}</span>
            <strong>{formatValue(d.value)}</strong>
          </div>
          <div style={{ background: "var(--color-neutral-soft)", borderRadius: 6, height: 10, overflow: "hidden" }}>
            <div
              style={{
                width: `${Math.min(100, (d.value / max) * 100)}%`,
                background: CHART_COLOR,
                height: "100%",
                borderRadius: 6,
              }}
            />
          </div>
        </div>
      ))}
    </div>
  );
}
