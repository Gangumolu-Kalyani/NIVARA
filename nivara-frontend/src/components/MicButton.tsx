import type { RecorderState } from "../hooks/useRecorder";

interface Props {
  state: RecorderState;
  secondsLeft: number;
  maxSeconds: number;
  disabled: boolean;
  onStart: () => void;
  onStop: () => void;
  onCancel: () => void;
}

/**
 * The microphone control: one tap to start, one tap to stop and send for transcription, or cancel
 * to throw the recording away. Always icon + text, never colour alone.
 */
export function MicButton({ state, secondsLeft, maxSeconds, disabled, onStart, onStop, onCancel }: Props) {
  if (state === "recording") {
    const elapsed = maxSeconds - secondsLeft;
    return (
      <div className="btn-row" style={{ alignItems: "center" }}>
        <button type="button" className="btn btn-danger" onClick={onStop} aria-label="Stop recording and transcribe">
          <span aria-hidden="true">⏹</span> Stop ({secondsLeft}s left)
        </button>
        <button type="button" className="btn" onClick={onCancel}>
          Cancel
        </button>
        <span className="status-pill danger" role="status" aria-live="polite">
          <span aria-hidden="true">●</span> Recording {elapsed}s
        </span>
      </div>
    );
  }
  return (
    <button
      type="button"
      className="btn"
      onClick={onStart}
      disabled={disabled || state === "requesting"}
      aria-label="Record a voice message"
    >
      <span aria-hidden="true">🎤</span> {state === "requesting" ? "Allow the microphone…" : "Speak"}
    </button>
  );
}
