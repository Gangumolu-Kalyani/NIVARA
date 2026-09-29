// Thin fetch wrapper: base URL, bearer token, JSON in/out (plus multipart uploads and binary
// downloads for voice), and typed errors. No other file in
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

/** A request body: JSON, or a multipart form (the browser sets its Content-Type and boundary). */
type RequestBody = { kind: "json"; value: unknown } | { kind: "form"; value: FormData };

async function send(method: string, path: string, body: RequestBody | undefined, accept: string): Promise<Response> {
  if (!navigator.onLine) {
    notifySync("offline");
    throw new ApiError(0, "This device is offline.");
  }
  notifySync("syncing");

  const headers: Record<string, string> = { Accept: accept };
  if (body?.kind === "json") {
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
      body: body === undefined ? undefined : body.kind === "json" ? JSON.stringify(body.value) : body.value,
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
  return response;
}

async function request<T>(method: string, path: string, body?: RequestBody): Promise<T> {
  const response = await send(method, path, body, "application/json");
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

const json = (value: unknown): RequestBody | undefined => (value === undefined ? undefined : { kind: "json", value });

/**
 * The user-facing message of a failed call: the "message" of a JSON error body when the server sent
 * one (the voice endpoints do), otherwise the given fallback. Never shows raw server output.
 */
export function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    try {
      const parsed = JSON.parse(error.message) as { message?: unknown };
      if (typeof parsed.message === "string" && parsed.message.trim()) {
        return parsed.message;
      }
    } catch {
      // not JSON
    }
  }
  return fallback;
}

export const api = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, json(body)),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, json(body)),
  del: <T>(path: string) => request<T>("DELETE", path),
  /** A multipart upload, such as a voice recording. */
  postForm: <T>(path: string, form: FormData) => request<T>("POST", path, { kind: "form", value: form }),
  /** A binary download, such as spoken audio. */
  getBlob: async (path: string): Promise<Blob> => (await send("GET", path, undefined, "*/*")).blob(),
};
