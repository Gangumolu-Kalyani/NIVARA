// Mirrors of the backend response/request DTOs this app actually uses.
// Kept in sync by hand with nivara-backend/src/main/java/com/sih/nivara/dto — see README
// "Integration points for Person 3" for the contract this was built against.

export type AccessLevel = "OWNER" | "EDITOR" | "VIEWER";
export type RelationshipType =
  | "SPOUSE" | "SON" | "DAUGHTER" | "SON_IN_LAW" | "DAUGHTER_IN_LAW" | "GRANDCHILD"
  | "SIBLING" | "RELATIVE" | "FRIEND" | "NEIGHBOUR" | "CAREGIVER" | "DOCTOR" | "OTHER";

export interface AccountResponse {
  uuid: string;
  fullName: string;
  email: string;
  phone: string | null;
  role: "ADMIN" | "CAREGIVER" | "PATIENT";
  preferredLanguage: string;
  lastLoginAt: string | null;
  createdAt: string;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  account: AccountResponse;
}

export interface PatientCaregiverResponse {
  patientUuid: string;
  caregiverUserUuid: string;
  caregiverFullName: string;
  relationship: RelationshipType;
  accessLevel: AccessLevel;
  primary: boolean;
  receivesAlerts: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PatientResponse {
  uuid: string;
  fullName: string;
  preferredName: string | null;
  birthYear: number | null;
  cognitiveStage: string;
  preferredLanguage: string;
  timezone: string;
  accessLevel: AccessLevel;
  createdAt: string;
  updatedAt: string;
}

// ---- Reminders / Daily Assistance -----------------------------------------------------------

export type ReminderCategory =
  | "MEDICINE" | "HYDRATION" | "APPOINTMENT" | "MOVEMENT" | "COGNITIVE_ACTIVITY" | "MEAL";
export type ReminderRepeatType = "DAILY" | "WEEKLY" | "ONCE";
export type DayOfWeekCode = "MON" | "TUE" | "WED" | "THU" | "FRI" | "SAT" | "SUN";
export type ReminderResponseStatus = "PENDING" | "SENT" | "SEEN" | "COMPLETED" | "MISSED" | "ESCALATED";
export type PatientResponseType = "TAKEN" | "REMIND_LATER" | "NEED_HELP";

export interface ReminderResponse {
  uuid: string;
  patientUuid: string;
  category: ReminderCategory;
  title: string;
  instructions: string | null;
  scheduledTime: string; // "HH:mm:ss"
  repeatType: ReminderRepeatType;
  repeatDays: DayOfWeekCode[];
  oneOffDate: string | null;
  escalateAfterMissed: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ReminderCreateRequest {
  category: ReminderCategory;
  title: string;
  instructions?: string | null;
  scheduledTime: string;
  repeatType?: ReminderRepeatType;
  repeatDays?: DayOfWeekCode[] | null;
  oneOffDate?: string | null;
  escalateAfterMissed?: number;
  active?: boolean;
}

export interface ReminderUpdateRequest {
  category: ReminderCategory;
  title: string;
  instructions?: string | null;
  scheduledTime: string;
  repeatType: ReminderRepeatType;
  repeatDays?: DayOfWeekCode[] | null;
  oneOffDate?: string | null;
  escalateAfterMissed: number;
  active: boolean;
}

export interface ReminderOccurrenceResponse {
  uuid: string;
  reminderUuid: string;
  reminderTitle: string;
  category: ReminderCategory;
  patientUuid: string;
  scheduledAt: string;
  notifiedAt: string | null;
  responseStatus: ReminderResponseStatus;
  responseType: PatientResponseType | null;
  respondedAt: string | null;
  nudgeCount: number;
  escalated: boolean;
  escalatedAt: string | null;
}

// ---- Alerts -----------------------------------------------------------------------------------

export type AlertSeverity = "HIGH" | "MEDIUM" | "LOW";
export type AlertStatus = "OPEN" | "RESOLVED" | "DISMISSED";
export type AlertCategory = ReminderCategory | "GENERAL";

export interface AlertResponse {
  uuid: string;
  patientUuid: string;
  reminderOccurrenceUuid: string | null;
  alertType: "REMINDER_ESCALATION" | "OTHER";
  category: AlertCategory;
  severity: AlertSeverity;
  title: string;
  message: string;
  status: AlertStatus;
  createdAt: string;
  resolvedAt: string | null;
  resolvedByName: string | null;
}

// ---- Dashboard / progress / daily summary --------------------------------------------------

export interface ActivityCard {
  completed: number;
  total: number;
}

export interface ActivityCards {
  cognitiveActivity: ActivityCard;
  medicine: ActivityCard;
  hydration: ActivityCard;
  movement: ActivityCard;
}

export type PatientCareStatus = "ON_TRACK" | "NEEDS_ATTENTION";

export interface DashboardSummaryResponse {
  patient: { uuid: string; fullName: string };
  careStatus: PatientCareStatus;
  dailyActivity: ActivityCards;
  reminderSummary: {
    totalToday: number;
    completedToday: number;
    pendingToday: number;
    missedToday: number;
    upcomingAppointments: number;
  };
  openAlerts: AlertResponse[];
  generatedAt: string;
}

export type CognitiveDomain =
  | "MEMORY" | "ATTENTION" | "LANGUAGE" | "EXECUTIVE_FUNCTION" | "ORIENTATION"
  | "VISUOSPATIAL" | "PROCESSING_SPEED";
export type GameResultStatus = "COMPLETED" | "ABANDONED";
export type PerformanceTrend = "UP" | "DOWN" | "STABLE" | "INSUFFICIENT_DATA";

export interface GameSummaryResponse {
  uuid: string;
  code: string;
  name: string;
  cognitiveDomain: CognitiveDomain;
}

export interface GameResultSummaryResponse {
  uuid: string;
  patientUuid: string;
  game: GameSummaryResponse;
  cognitiveDomain: CognitiveDomain;
  difficultyLevel: number;
  status: GameResultStatus;
  startedAt: string;
  completedAt: string | null;
  durationMs: number;
  score: number;
  maxScore: number | null;
  totalQuestions: number;
  correctAnswers: number;
  mistakes: number;
  answerAttempts: number;
  hintsUsed: number;
  avgReactionTimeMs: number | null;
  accuracy: number | null;
  playedOffline: boolean;
  languageCode: string | null;
  createdAt: string;
}

export interface DailyAccuracyPoint {
  date: string;
  averageAccuracy: number | null;
  gamesPlayed: number;
}

export interface ProgressResponse {
  rangeDays: number;
  gamesCompleted: number;
  gamesAbandoned: number;
  averageAccuracy: number | null;
  averageReactionTimeMs: number | null;
  difficultyDistribution: Record<string, number>;
  domainAccuracy: Partial<Record<CognitiveDomain, number>>;
  overallAccuracyTrend: PerformanceTrend;
  accuracyTrend: DailyAccuracyPoint[];
  recentActivities: GameResultSummaryResponse[];
}

export type CareRhythmLevel = "STRONG" | "NEEDS_PROMPTING" | "LOW" | "NO_DATA";

export interface DailySummaryResponse {
  date: string;
  dailyActivity: ActivityCards;
  upcomingAppointments: number;
  summaryLines: string[];
  careRhythm: { morning: CareRhythmLevel; afternoon: CareRhythmLevel; evening: CareRhythmLevel };
  patterns: string[];
}
