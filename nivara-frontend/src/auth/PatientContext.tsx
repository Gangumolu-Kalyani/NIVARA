import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { PatientsApi } from "../api/endpoints";
import type { PatientResponse } from "../types/api";
import { useAuth } from "./AuthContext";

interface PatientState {
  patients: PatientResponse[];
  selected: PatientResponse | null;
  loading: boolean;
  error: string | null;
  selectPatient: (uuid: string) => void;
  reload: () => void;
}

const PatientContext = createContext<PatientState | undefined>(undefined);
const STORAGE_KEY = "nivara.selectedPatient";

export function PatientProvider({ children }: { children: ReactNode }) {
  const { token } = useAuth();
  const [patients, setPatients] = useState<PatientResponse[]>([]);
  const [selectedUuid, setSelectedUuid] = useState<string | null>(() => localStorage.getItem(STORAGE_KEY));
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadTick, setReloadTick] = useState(0);

  useEffect(() => {
    if (!token) {
      setPatients([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    PatientsApi.list()
      .then((list) => {
        setPatients(list);
        if (!selectedUuid && list.length > 0) {
          setSelectedUuid(list[0].uuid);
          localStorage.setItem(STORAGE_KEY, list[0].uuid);
        }
      })
      .catch(() => setError("Could not load patients."))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, reloadTick]);

  const value = useMemo<PatientState>(
    () => ({
      patients,
      selected: patients.find((p) => p.uuid === selectedUuid) ?? null,
      loading,
      error,
      selectPatient(uuid) {
        setSelectedUuid(uuid);
        localStorage.setItem(STORAGE_KEY, uuid);
      },
      reload() {
        setReloadTick((tick) => tick + 1);
      },
    }),
    [patients, selectedUuid, loading, error],
  );

  return <PatientContext.Provider value={value}>{children}</PatientContext.Provider>;
}

export function usePatients(): PatientState {
  const context = useContext(PatientContext);
  if (!context) {
    throw new Error("usePatients must be used within PatientProvider");
  }
  return context;
}
