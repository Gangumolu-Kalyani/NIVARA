import { useCallback, useEffect, useRef, useState } from "react";

/** A finished recording, ready to upload. */
export interface Recording {
  blob: Blob;
  fileName: string;
  seconds: number;
}

export type RecorderState = "idle" | "requesting" | "recording";

// Opus in WebM is what Chrome, Edge and Firefox record; Safari records AAC in MP4.
const PREFERRED_TYPES = ["audio/webm;codecs=opus", "audio/webm", "audio/mp4", "audio/ogg;codecs=opus"];

function pickMimeType(): string | undefined {
  if (typeof MediaRecorder === "undefined" || typeof MediaRecorder.isTypeSupported !== "function") {
    return undefined;
  }
  return PREFERRED_TYPES.find((type) => MediaRecorder.isTypeSupported(type));
}

function extensionFor(mimeType: string): string {
  if (mimeType.startsWith("audio/mp4")) return "m4a";
  if (mimeType.startsWith("audio/ogg")) return "ogg";
  return "webm";
}

/** A plain-language reason recording could not start. */
function describe(error: unknown): string {
  const name = error instanceof DOMException ? error.name : "";
  if (name === "NotAllowedError" || name === "SecurityError") {
    return "Microphone permission was denied. Allow the microphone in your browser settings, or type instead.";
  }
  if (name === "NotFoundError" || name === "OverconstrainedError") {
    return "No microphone was found on this device. You can type instead.";
  }
  if (name === "NotReadableError") {
    return "The microphone is in use by another app. Close it and try again.";
  }
  return "Recording could not start. You can type instead.";
}

/**
 * Records one voice message with the browser's MediaRecorder: at most maxSeconds, then it stops by
 * itself. onRecorded receives the finished recording; cancel() discards it. The microphone is
 * released as soon as recording ends, and when the component unmounts.
 */
export function useRecorder(maxSeconds: number, onRecorded: (recording: Recording) => void) {
  const [state, setState] = useState<RecorderState>("idle");
  const [elapsed, setElapsed] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const recorderRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const timerRef = useRef<number | null>(null);
  const startedAtRef = useRef(0);
  const cancelledRef = useRef(false);
  const onRecordedRef = useRef(onRecorded);
  useEffect(() => {
    onRecordedRef.current = onRecorded;
  }, [onRecorded]);

  const release = useCallback(() => {
    if (timerRef.current !== null) {
      window.clearInterval(timerRef.current);
      timerRef.current = null;
    }
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    recorderRef.current = null;
  }, []);

  const stop = useCallback(() => {
    const recorder = recorderRef.current;
    if (recorder && recorder.state !== "inactive") {
      recorder.stop();
    }
  }, []);

  const cancel = useCallback(() => {
    cancelledRef.current = true;
    stop();
  }, [stop]);

  const start = useCallback(async () => {
    setError(null);
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === "undefined") {
      setError("Voice recording needs a secure (https) connection and a browser that supports it. You can type instead.");
      return;
    }
    setState("requesting");
    let stream: MediaStream;
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch (e) {
      setState("idle");
      setError(describe(e));
      return;
    }

    const mimeType = pickMimeType();
    let recorder: MediaRecorder;
    try {
      recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream);
    } catch (e) {
      stream.getTracks().forEach((track) => track.stop());
      setState("idle");
      setError(describe(e));
      return;
    }

    const chunks: Blob[] = [];
    cancelledRef.current = false;
    streamRef.current = stream;
    recorderRef.current = recorder;
    recorder.ondataavailable = (event) => {
      if (event.data.size > 0) chunks.push(event.data);
    };
    recorder.onstop = () => {
      const seconds = (Date.now() - startedAtRef.current) / 1000;
      const type = recorder.mimeType || mimeType || "audio/webm";
      release();
      setState("idle");
      setElapsed(0);
      if (cancelledRef.current || chunks.length === 0) return;
      const blob = new Blob(chunks, { type });
      onRecordedRef.current({ blob, fileName: `recording.${extensionFor(type)}`, seconds });
    };

    startedAtRef.current = Date.now();
    setElapsed(0);
    recorder.start();
    setState("recording");
    timerRef.current = window.setInterval(() => {
      const seconds = Math.floor((Date.now() - startedAtRef.current) / 1000);
      setElapsed(seconds);
      if (seconds >= maxSeconds) stop();
    }, 250);
  }, [maxSeconds, release, stop]);

  // Never leave the microphone on after leaving the page
  useEffect(() => () => {
    cancelledRef.current = true;
    const recorder = recorderRef.current;
    if (recorder && recorder.state !== "inactive") recorder.stop();
    release();
  }, [release]);

  return { state, elapsed, secondsLeft: Math.max(0, maxSeconds - elapsed), error, clearError: () => setError(null), start, stop, cancel };
}
