import { useState, type FormEvent } from "react";
import { useAuth } from "../auth/AuthContext";
import { ApiError } from "../api/client";

export function LoginPage() {
  const { login, register } = useAuth();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      if (mode === "register") {
        await register(fullName, email, password);
        setMode("login");
        setError("Account created. Sign in below.");
      } else {
        await login(email, password);
      }
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        setError("That email or password is not right.");
      } else if (err instanceof ApiError && err.status === 0) {
        setError("Could not reach the NIVARA server. Check your connection.");
      } else {
        setError("Something went wrong. Please try again.");
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="center-screen">
      <form className="card auth-card" onSubmit={handleSubmit}>
        <h1 className="greeting" style={{ fontSize: "var(--font-size-xl)" }}>
          {mode === "login" ? "Welcome back" : "Create your caregiver account"}
        </h1>
        <p className="subtitle" style={{ marginBottom: "var(--space-5)" }}>
          NIVARA caregiver dashboard
        </p>

        {mode === "register" && (
          <div className="field">
            <label htmlFor="fullName">Your name</label>
            <input id="fullName" value={fullName} onChange={(e) => setFullName(e.target.value)} required />
          </div>
        )}
        <div className="field">
          <label htmlFor="email">Email</label>
          <input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </div>
        <div className="field">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            minLength={8}
            required
          />
        </div>

        {error && (
          <p role="alert" className="status-pill warning" style={{ marginBottom: "var(--space-4)" }}>
            {error}
          </p>
        )}

        <button type="submit" className="btn btn-primary btn-block" disabled={busy}>
          {mode === "login" ? "Sign in" : "Create account"}
        </button>
        <button
          type="button"
          className="btn btn-block"
          style={{ marginTop: "var(--space-3)", background: "transparent", border: "none" }}
          onClick={() => setMode(mode === "login" ? "register" : "login")}
        >
          {mode === "login" ? "New caregiver? Create an account" : "Already have an account? Sign in"}
        </button>
      </form>
    </div>
  );
}
