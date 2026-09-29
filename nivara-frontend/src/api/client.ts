// Thin fetch wrapper: base URL, bearer token, JSON in/out, and typed errors. No other file in
// this app calls fetch() directly, so the auth header and the offline/sync signal (Module 14)
// stay in exactly one place.

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

let authToken: string | null = null;
export function setAuthToken(token: string | null) {
  authToken = token;
}

type SyncListener = (state: "synced" | "syncing" | "offline") => void;
const syncListeners = new Set<SyncListener>();
export function onSyncStateChange(listener: SyncListener): () => void {
  syncListeners.add(listener);
  return () => syncListeners.delete(listener);
}
function notifySync(state: "synced" | "syncing" | "offline") {
  syncListeners.forEach((listener) => listener(state));
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  if (!navigator.onLine) {
    notifySync("offline");
    throw new ApiError(0, "This device is offline.");
  }
  notifySync("syncing");

  const headers: Record<string, string> = { Accept: "application/json" };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (authToken) {
    headers.Authorization = `Bearer ${authToken}`;
  }

  let response: Response;
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    notifySync("offline");
    throw new ApiError(0, "Could not reach the NIVARA server.");
  }

  if (!response.ok) {
    notifySync("synced");
    const text = await response.text().catch(() => "");
    throw new ApiError(response.status, text || response.statusText);
  }

  notifySync("synced");
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const api = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, body),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, body),
  del: <T>(path: string) => request<T>("DELETE", path),
};
