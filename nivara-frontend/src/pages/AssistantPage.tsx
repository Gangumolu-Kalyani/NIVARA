import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { AssistantApi } from "../api/endpoints";
import { errorMessage } from "../api/client";
import { usePatients } from "../auth/PatientContext";
import { MicButton } from "../components/MicButton";
import { PatientSelector } from "../components/PatientSelector";
import { useRecorder, type Recording } from "../hooks/useRecorder";
import type { AssistantMessageResponse, ConversationResponse } from "../types/api";

/** Sarvam's synchronous speech-to-text accepts at most 30 seconds. */
const MAX_RECORDING_SECONDS = 30;

type Phase = "idle" | "transcribing" | "thinking" | "speaking";

const PHASE_LABEL: Record<Phase, string> = {
  idle: "",
  transcribing: "Listening to your recording…",
  thinking: "The assistant is thinking…",
  speaking: "Speaking…",
};

interface Problem {
  message: string;
  retry?: () => void;
}

/**
 * The caregiver's assistant: conversations about a patient, by typing or by voice.
 *
 * Voice reuses the text assistant: a recording is transcribed (nothing is stored), the transcript
 * lands in the text box to check, and sending it is an ordinary message. A reply to a spoken
 * message is read aloud; any reply can be played or stopped.
 */
export function AssistantPage() {
  const { selected } = usePatients();
  const [conversations, setConversations] = useState<ConversationResponse[]>([]);
  const [activeUuid, setActiveUuid] = useState<string | null>(null);
  const [messages, setMessages] = useState<AssistantMessageResponse[]>([]);
  const [draft, setDraft] = useState("");
  const [draftFromVoice, setDraftFromVoice] = useState(false);
  const [phase, setPhase] = useState<Phase>("idle");
  const [playingUuid, setPlayingUuid] = useState<string | null>(null);
  const [problem, setProblem] = useState<Problem | null>(null);
  const [loading, setLoading] = useState(true);

  const audioRef = useRef<HTMLAudioElement | null>(null);
  const audioUrlRef = useRef<string | null>(null);
  const draftRef = useRef<HTMLTextAreaElement | null>(null);
  const bottomRef = useRef<HTMLDivElement | null>(null);

  const active = conversations.find((c) => c.uuid === activeUuid) ?? null;
  const closed = active?.status === "CLOSED";
  const busy = phase === "transcribing" || phase === "thinking";

  // ---- playback --------------------------------------------------------------------------------

  const stopPlayback = useCallback(() => {
    audioRef.current?.pause();
    audioRef.current = null;
    if (audioUrlRef.current) {
      URL.revokeObjectURL(audioUrlRef.current);
      audioUrlRef.current = null;
    }
    setPlayingUuid(null);
    setPhase((p) => (p === "speaking" ? "idle" : p));
  }, []);

  useEffect(() => stopPlayback, [stopPlayback]);

  const play = useCallback(
    async (conversationUuid: string, message: AssistantMessageResponse) => {
      stopPlayback();
      setProblem(null);
      setPhase("speaking");
      setPlayingUuid(message.uuid);
      try {
        const audioBlob = await AssistantApi.speech(conversationUuid, message.uuid);
        const url = URL.createObjectURL(audioBlob);
        audioUrlRef.current = url;
        const audio = new Audio(url);
        audioRef.current = audio;
        audio.onended = stopPlayback;
        audio.onerror = () => {
          stopPlayback();
          setProblem({ message: "This reply could not be played.", retry: () => play(conversationUuid, message) });
        };
        await audio.play();
      } catch (e) {
        stopPlayback();
        setProblem({
          message: errorMessage(e, "The reply could not be spoken. The text is shown above."),
          retry: () => play(conversationUuid, message),
        });
      }
    },
    [stopPlayback],
  );

  // ---- conversations ---------------------------------------------------------------------------

  const loadConversation = useCallback(
    async (uuid: string) => {
      stopPlayback();
      setActiveUuid(uuid);
      setProblem(null);
      try {
        const detail = await AssistantApi.get(uuid);
        setMessages(detail.messages);
      } catch (e) {
        setMessages([]);
        setProblem({ message: errorMessage(e, "This conversation could not be loaded."), retry: () => loadConversation(uuid) });
      }
    },
    [stopPlayback],
  );

  useEffect(() => {
    let cancelled = false;
    AssistantApi.list()
      .then((list) => {
        if (cancelled) return;
        setConversations(list);
        if (list.length > 0) void loadConversation(list[0].uuid);
      })
      .catch(() => !cancelled && setProblem({ message: "Your conversations could not be loaded." }))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [loadConversation]);

  async function startConversation() {
    if (!selected) return;
    setProblem(null);
    try {
      const created = await AssistantApi.start(selected.uuid);
      setConversations((list) => [created, ...list]);
      setMessages([]);
      setActiveUuid(created.uuid);
    } catch (e) {
      setProblem({ message: errorMessage(e, "A new conversation could not be started."), retry: startConversation });
    }
  }

  // ---- sending ---------------------------------------------------------------------------------

  async function send(text: string, speakReply: boolean) {
    if (!activeUuid || !text.trim()) return;
    const conversationUuid = activeUuid;
    stopPlayback();
    setProblem(null);
    setPhase("thinking");
    try {
      const exchange = await AssistantApi.send(conversationUuid, text.trim());
      setMessages((list) => [...list, exchange.userMessage, exchange.reply]);
      setDraft("");
      setDraftFromVoice(false);
      setConversations((list) =>
        [...list].sort((a, b) => (a.uuid === conversationUuid ? -1 : b.uuid === conversationUuid ? 1 : 0)),
      );
      setPhase("idle");
      if (speakReply) void play(conversationUuid, exchange.reply);
    } catch (e) {
      setPhase("idle");
      // The draft stays in the box, so nothing typed or heard is lost
      setProblem({ message: errorMessage(e, "Your message could not be sent."), retry: () => send(text, speakReply) });
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    void send(draft, draftFromVoice);
  }

  // ---- voice -----------------------------------------------------------------------------------

  const transcribe = useCallback(
    async (recording: Recording) => {
      if (!activeUuid) return;
      setProblem(null);
      setPhase("transcribing");
      try {
        const result = await AssistantApi.transcribe(activeUuid, recording.blob, recording.fileName);
        // Nothing is stored yet: the caregiver checks the words, then sends them
        setDraft(result.transcript);
        setDraftFromVoice(true);
        setPhase("idle");
        draftRef.current?.focus();
      } catch (e) {
        setPhase("idle");
        setProblem({
          message: errorMessage(e, "Your recording could not be understood. Please try again or type."),
          retry: () => transcribe(recording),
        });
      }
    },
    [activeUuid],
  );

  const recorder = useRecorder(MAX_RECORDING_SECONDS, (recording) => void transcribe(recording));

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: "end" });
  }, [messages.length]);

  // ---- view ------------------------------------------------------------------------------------

  const status = recorder.state === "recording" ? "" : PHASE_LABEL[phase];
  const shownProblem = recorder.error ? { message: recorder.error } : problem;

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="greeting" style={{ fontSize: "var(--font-size-xl)" }}>
            Assistant
          </h1>
          <PatientSelector />
        </div>
        <button type="button" className="btn btn-primary" onClick={startConversation} disabled={!selected}>
          + New conversation
        </button>
      </div>

      <div style={{ display: "flex", flexWrap: "wrap", gap: "var(--space-4)", alignItems: "flex-start" }}>
        <section className="card" style={{ flex: "1 1 220px", maxWidth: 360 }} aria-label="Conversations">
          <h2 className="section-title" style={{ marginTop: 0 }}>
            Conversations
          </h2>
          {loading && <p className="subtitle">Loading…</p>}
          {!loading && conversations.length === 0 && (
            <p className="empty-state">No conversations yet. Start one about the selected patient.</p>
          )}
          <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
            {conversations.map((c) => (
              <button
                key={c.uuid}
                type="button"
                className={`chip ${c.uuid === activeUuid ? "selected" : ""}`}
                style={{ textAlign: "left", justifyContent: "flex-start" }}
                onClick={() => loadConversation(c.uuid)}
                aria-current={c.uuid === activeUuid}
              >
                {c.patientName ?? "General"} · {new Date(c.updatedAt).toLocaleString([], { dateStyle: "short", timeStyle: "short" })}
                {c.status === "CLOSED" ? " · closed" : ""}
              </button>
            ))}
          </div>
        </section>

        <section className="card" style={{ flex: "3 1 360px", minWidth: 0 }} aria-label="Chat">
          {!active ? (
            <p className="empty-state">Choose a conversation, or start a new one.</p>
          ) : (
            <>
              <div
                style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)", maxHeight: "55vh", overflowY: "auto" }}
                aria-live="polite"
              >
                {messages.length === 0 && (
                  <p className="subtitle">Ask about today's reminders, alerts, people or memories. Type or tap Speak.</p>
                )}
                {messages.map((m) => {
                  const mine = m.sender === "USER";
                  const playing = playingUuid === m.uuid;
                  return (
                    <div
                      key={m.uuid}
                      style={{
                        alignSelf: mine ? "flex-end" : "flex-start",
                        maxWidth: "85%",
                        padding: "var(--space-3)",
                        borderRadius: 12,
                        background: mine ? "var(--color-surface-alt, rgba(0,0,0,0.05))" : "transparent",
                        border: "1px solid var(--color-border, rgba(0,0,0,0.12))",
                      }}
                    >
                      <div className="subtitle" style={{ margin: 0, fontSize: "var(--font-size-sm)" }}>
                        {mine ? "You" : "Assistant"}
                      </div>
                      <p style={{ margin: "var(--space-1) 0 0", whiteSpace: "pre-wrap" }}>{m.content}</p>
                      {!mine && (
                        <button
                          type="button"
                          className="btn"
                          style={{ marginTop: "var(--space-2)" }}
                          onClick={() => (playing ? stopPlayback() : play(active.uuid, m))}
                          aria-label={playing ? "Stop playback" : "Play this reply aloud"}
                        >
                          <span aria-hidden="true">{playing ? "⏹" : "🔊"}</span> {playing ? "Stop" : "Play"}
                        </button>
                      )}
                    </div>
                  );
                })}
                <div ref={bottomRef} />
              </div>

              {status && (
                <p className="subtitle" role="status" aria-live="polite" style={{ marginTop: "var(--space-3)" }}>
                  {status}
                </p>
              )}

              {shownProblem && (
                <div className="status-pill warning" role="alert" style={{ marginTop: "var(--space-3)", display: "flex", gap: "var(--space-2)", flexWrap: "wrap" }}>
                  <span>⚠ {shownProblem.message}</span>
                  {"retry" in shownProblem && shownProblem.retry && (
                    <button type="button" className="btn" onClick={shownProblem.retry}>
                      Retry
                    </button>
                  )}
                  <button
                    type="button"
                    className="btn"
                    onClick={() => {
                      recorder.clearError();
                      setProblem(null);
                    }}
                  >
                    Dismiss
                  </button>
                </div>
              )}

              {closed ? (
                <p className="subtitle" style={{ marginTop: "var(--space-4)" }}>
                  This conversation is closed. Start a new one to continue.
                </p>
              ) : (
                <form onSubmit={submit} style={{ marginTop: "var(--space-4)" }}>
                  <div className="field">
                    <label htmlFor="assistant-draft">
                      {draftFromVoice ? "Check what was heard, then send" : "Your message"}
                    </label>
                    <textarea
                      id="assistant-draft"
                      ref={draftRef}
                      value={draft}
                      maxLength={4000}
                      rows={3}
                      disabled={busy}
                      onChange={(e) => {
                        setDraft(e.target.value);
                        if (!e.target.value) setDraftFromVoice(false);
                      }}
                      placeholder="How did she do today?"
                    />
                  </div>
                  <div className="btn-row" style={{ alignItems: "center" }}>
                    <button type="submit" className="btn btn-primary" disabled={busy || !draft.trim()}>
                      {phase === "thinking" ? "Sending…" : "Send"}
                    </button>
                    <MicButton
                      state={recorder.state}
                      secondsLeft={recorder.secondsLeft}
                      maxSeconds={MAX_RECORDING_SECONDS}
                      disabled={busy}
                      onStart={() => {
                        stopPlayback();
                        setProblem(null);
                        void recorder.start();
                      }}
                      onStop={recorder.stop}
                      onCancel={recorder.cancel}
                    />
                  </div>
                </form>
              )}
            </>
          )}
        </section>
      </div>
    </div>
  );
}
