import { usePatients } from "../auth/PatientContext";

/** "Lakshmi Rao" patient selector (Module 1). A no-op single select when there is one patient. */
export function PatientSelector() {
  const { patients, selected, selectPatient } = usePatients();

  if (patients.length === 0) {
    return null;
  }

  if (patients.length === 1) {
    return <p className="subtitle">{selected?.fullName}</p>;
  }

  return (
    <select
      aria-label="Selected patient"
      value={selected?.uuid ?? ""}
      onChange={(event) => selectPatient(event.target.value)}
      style={{
        minHeight: 44,
        borderRadius: "var(--radius-pill)",
        border: "1px solid var(--color-border)",
        padding: "0 16px",
        fontSize: "var(--font-size-base)",
        fontWeight: 600,
        background: "var(--color-surface)",
        color: "var(--color-text)",
      }}
    >
      {patients.map((patient) => (
        <option key={patient.uuid} value={patient.uuid}>
          {patient.fullName}
        </option>
      ))}
    </select>
  );
}
