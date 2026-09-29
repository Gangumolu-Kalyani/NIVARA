import { api } from "./client";
import type {
  AccountResponse,
  AlertResponse,
  AlertStatus,
  DailySummaryResponse,
  DashboardSummaryResponse,
  PatientCaregiverResponse,
  PatientResponse,
  ProgressResponse,
  ReminderCreateRequest,
  ReminderOccurrenceResponse,
  ReminderResponse,
  ReminderUpdateRequest,
  TokenResponse,
} from "../types/api";

export const AuthApi = {
  register: (fullName: string, email: string, password: string) =>
    api.post<void>("/api/auth/register", { fullName, email, password }),
  login: (email: string, password: string) =>
    api.post<TokenResponse>("/api/auth/login", { email, password }),
  me: () => api.get<AccountResponse>("/api/auth/me"),
};

export const PatientsApi = {
  list: () => api.get<PatientResponse[]>("/api/patients"),
  get: (uuid: string) => api.get<PatientResponse>(`/api/patients/${uuid}`),
  create: (fullName: string) => api.post<PatientResponse>("/api/patients", { fullName }),
  caregivers: (uuid: string) => api.get<PatientCaregiverResponse[]>(`/api/patients/${uuid}/caregivers`),
};

export const DashboardApi = {
  summary: (patientUuid: string) => api.get<DashboardSummaryResponse>(`/api/patients/${patientUuid}/dashboard`),
  progress: (patientUuid: string, range: 7 | 30) =>
    api.get<ProgressResponse>(`/api/patients/${patientUuid}/progress?range=${range}`),
  dailySummary: (patientUuid: string, date?: string) =>
    api.get<DailySummaryResponse>(
      `/api/patients/${patientUuid}/daily-summary${date ? `?date=${date}` : ""}`,
    ),
};

export const RemindersApi = {
  list: (patientUuid: string) => api.get<ReminderResponse[]>(`/api/patients/${patientUuid}/reminders`),
  get: (uuid: string) => api.get<ReminderResponse>(`/api/reminders/${uuid}`),
  create: (patientUuid: string, body: ReminderCreateRequest) =>
    api.post<ReminderResponse>(`/api/patients/${patientUuid}/reminders`, body),
  update: (uuid: string, body: ReminderUpdateRequest) => api.put<ReminderResponse>(`/api/reminders/${uuid}`, body),
  remove: (uuid: string) => api.del<void>(`/api/reminders/${uuid}`),
  recordResponse: (
    reminderUuid: string,
    responseType: "TAKEN" | "REMIND_LATER" | "NEED_HELP",
    scheduledAt?: string,
  ) =>
    api.post<ReminderOccurrenceResponse>(`/api/reminders/${reminderUuid}/responses`, {
      responseType,
      scheduledAt,
    }),
  history: (reminderUuid: string) =>
    api.get<ReminderOccurrenceResponse[]>(`/api/reminders/${reminderUuid}/responses`),
  dailyCare: (patientUuid: string, date: string) =>
    api.get<ReminderOccurrenceResponse[]>(`/api/patients/${patientUuid}/daily-care?date=${date}`),
};

export const AlertsApi = {
  list: (patientUuid: string, status?: AlertStatus) =>
    api.get<AlertResponse[]>(`/api/patients/${patientUuid}/alerts${status ? `?status=${status}` : ""}`),
  get: (uuid: string) => api.get<AlertResponse>(`/api/alerts/${uuid}`),
  resolve: (uuid: string) => api.put<AlertResponse>(`/api/alerts/${uuid}/resolve`),
  dismiss: (uuid: string) => api.put<AlertResponse>(`/api/alerts/${uuid}/dismiss`),
};
