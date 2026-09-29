import { Navigate, Route, BrowserRouter as Router, Routes } from "react-router-dom";
import { AppNav } from "./components/AppNav";
import { AuthProvider, useAuth } from "./auth/AuthContext";
import { PatientProvider } from "./auth/PatientContext";
import { LoginPage } from "./pages/LoginPage";
import { OverviewPage } from "./pages/OverviewPage";
import { ProgressPage } from "./pages/ProgressPage";
import { DailyCarePage } from "./pages/DailyCarePage";
import { AlertsPage } from "./pages/AlertsPage";
import { AlertDetailPage } from "./pages/AlertDetailPage";
import { PatientPage } from "./pages/PatientPage";

function AuthedApp() {
  return (
    <PatientProvider>
      <div className="app-shell">
        <AppNav />
        <Routes>
          <Route path="/overview" element={<OverviewPage />} />
          <Route path="/progress" element={<ProgressPage />} />
          <Route path="/daily-care" element={<DailyCarePage />} />
          <Route path="/alerts" element={<AlertsPage />} />
          <Route path="/alerts/:uuid" element={<AlertDetailPage />} />
          <Route path="/patient" element={<PatientPage />} />
          <Route path="*" element={<Navigate to="/overview" replace />} />
        </Routes>
      </div>
    </PatientProvider>
  );
}

function Root() {
  const { token, loading } = useAuth();

  if (loading) {
    return (
      <div className="center-screen">
        <p className="subtitle">Loading…</p>
      </div>
    );
  }

  return token ? <AuthedApp /> : <LoginPage />;
}

export default function App() {
  return (
    <Router>
      <AuthProvider>
        <Root />
      </AuthProvider>
    </Router>
  );
}
