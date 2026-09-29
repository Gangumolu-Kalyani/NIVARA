import { useEffect, useState } from "react";
import { DashboardApi, PatientsApi } from "../api/endpoints";
import { usePatients } from "../auth/PatientContext";
import { PatientSelector } from "../components/PatientSelector";
import type { DashboardSummaryResponse, PatientCaregiverResponse } from "../types/api";

/** Module 13: a simple patient header — a daily-care status, never framed as a medical status. */
export function PatientPage() {
  const { selected } = usePatients();
  const [summary, setSummary] = useState<DashboardSummaryResponse | null>(null);
  const [team, setTeam] = useState<PatientCaregiverResponse[]>([]);

  useEffect(() => {
    if (!selected) return;
    DashboardApi.summary(selected.uuid).then(setSummary);
    PatientsApi.caregivers(selected.uuid).then(setTeam);
  }, [selected]);

  if (!selected) {
    return (
      <div className="page">
        <p className="empty-state">No patient selected yet.</p>
      </div>
    );
  }

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="greeting">{selected.fullName}</h1>
          <PatientSelector />
        </div>
      </div>

      {summary && (
        <div className={`status-pill ${summary.careStatus === "ON_TRACK" ? "success" : "warning"}`} style={{ marginBottom: "var(--space-5)" }}>
          <span aria-hidden="true">{summary.careStatus === "ON_TRACK" ? "✓" : "⚠"}</span>
          Today's care status: {summary.careStatus === "ON_TRACK" ? "On Track" : "Needs Attention"}
        </div>
      )}

      <div className="card">
        <dl style={{ display: "grid", gridTemplateColumns: "auto 1fr", gap: "var(--space-2) var(--space-4)" }}>
          {selected.preferredName && (
            <>
              <dt className="subtitle">Called</dt>
              <dd style={{ margin: 0 }}>{selected.preferredName}</dd>
            </>
          )}
          {selected.birthYear && (
            <>
              <dt className="subtitle">Birth year</dt>
              <dd style={{ margin: 0 }}>{selected.birthYear}</dd>
            </>
          )}
          <dt className="subtitle">Preferred language</dt>
          <dd style={{ margin: 0 }}>{selected.preferredLanguage}</dd>
          <dt className="subtitle">Timezone</dt>
          <dd style={{ margin: 0 }}>{selected.timezone}</dd>
        </dl>
      </div>

      <h2 className="section-title">Care Team</h2>
      {team.length === 0 ? (
        <p className="empty-state">No care team information yet.</p>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)" }}>
          {team.map((member) => (
            <div key={member.caregiverUserUuid} className="card" style={{ display: "flex", justifyContent: "space-between" }}>
              <div>
                <strong>{member.caregiverFullName}</strong>
                <p className="subtitle" style={{ margin: 0 }}>
                  {member.relationship} {member.primary ? "· Primary caregiver" : ""}
                </p>
              </div>
              <span className="status-pill neutral">{member.accessLevel}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
