package com.sih.nivara.assistant.service;

import com.sih.nivara.assistant.entity.AssistantConversation;
import com.sih.nivara.assistant.entity.AssistantMessage;
import com.sih.nivara.assistant.entity.enums.MessageSender;
import com.sih.nivara.assistant.speech.AudioClip;
import com.sih.nivara.assistant.speech.AudioUploads;
import com.sih.nivara.assistant.speech.SpeechAudio;
import com.sih.nivara.assistant.speech.SpeechException;
import com.sih.nivara.assistant.speech.SpeechProperties;
import com.sih.nivara.assistant.speech.SpeechToText;
import com.sih.nivara.assistant.speech.TextToSpeech;
import com.sih.nivara.assistant.speech.Transcript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.UUID;

/**
 * Voice for the assistant, around the unchanged text pipeline: turns a recording into text for the
 * caller to send as an ordinary message, and turns an assistant reply into speech.
 *
 * <p>Neither direction creates, changes or stores anything. A transcript only becomes a message
 * when the caller sends it through the existing message endpoint; audio in either direction lives
 * only for the request. Nothing about a recording, a transcript or a reply is logged: failures log
 * their reason only.
 *
 * <p>Every call first authorizes exactly as the text assistant does
 * ({@link AssistantService#authorizeConversation}), and a recording is validated for size and
 * format, so no unauthorized or unusable audio ever reaches a speech provider.
 */
@Service
public class VoiceService {

    private static final Logger log = LoggerFactory.getLogger(VoiceService.class);

    /** The longest transcript kept, matching what the message endpoint accepts. */
    static final int MAX_TRANSCRIPT_CHARS = 4000;

    static final String VOICE_UNAVAILABLE = "Voice is not available right now. Please try again, or type your message.";
    static final String VOICE_NOT_SET_UP = "Voice is not set up for this assistant.";
    static final String NOTHING_HEARD = "I didn't catch that. Please try again.";
    static final String LANGUAGE_NOT_SPOKEN = "Spoken replies are not available in this conversation's language.";

    /** What was heard, and in which language the conversation is held. */
    public record TranscriptionResult(String transcript, String languageCode, String detectedLanguageCode) {
    }

    private final AssistantService assistantService;
    private final ConversationService conversationService;
    private final SpeechToText speechToText;
    private final TextToSpeech textToSpeech;
    private final SpeechProperties properties;

    public VoiceService(AssistantService assistantService,
                        ConversationService conversationService,
                        SpeechToText speechToText,
                        TextToSpeech textToSpeech,
                        SpeechProperties properties) {
        this.assistantService = assistantService;
        this.conversationService = conversationService;
        this.speechToText = speechToText;
        this.textToSpeech = textToSpeech;
        this.properties = properties;
    }

    /**
     * Transcribes a recording for one of the caller's conversations, in its language, and creates
     * no message. Checks, in order, before any provider call: the caller and conversation (401,
     * 404), that it is still open (409), a recording was sent (400), its size (413) and that it is
     * not too short to hold speech (400), and its format (415). Then 422 when no speech was heard,
     * 503 when the provider fails.
     */
    public TranscriptionResult transcribe(UUID conversationUuid, MultipartFile audio) {
        AssistantConversation conversation = assistantService.authorizeConversation(conversationUuid).conversation();
        if (!conversation.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This conversation is closed");
        }
        if (audio == null || audio.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No recording was received");
        }
        if (audio.getSize() > properties.maxAudioBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The recording is too large");
        }
        byte[] bytes;
        try {
            bytes = audio.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The recording could not be read");
        }
        if (bytes.length < properties.minAudioBytes()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The recording is too short");
        }
        String mediaType = AudioUploads.acceptedMediaType(audio.getContentType(), bytes)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "This recording format is not supported"));

        Transcript transcript;
        try {
            transcript = speechToText.transcribe(new AudioClip(bytes, mediaType), conversation.getLanguageCode());
        } catch (SpeechException e) {
            throw unavailable("Transcription", e);
        }
        if (transcript.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, NOTHING_HEARD);
        }
        String text = transcript.text().strip();
        if (text.length() > MAX_TRANSCRIPT_CHARS) {
            text = text.substring(0, MAX_TRANSCRIPT_CHARS);
        }
        return new TranscriptionResult(text, conversation.getLanguageCode(), transcript.detectedLanguageCode());
    }

    /**
     * Speaks one assistant reply of one of the caller's conversations, in its language. 404 for a
     * message that is not in this conversation or is not the assistant's; 422 when the language
     * cannot be spoken; 503 when voice is off or the provider fails.
     */
    public SpeechAudio speak(UUID conversationUuid, UUID messageUuid) {
        AssistantConversation conversation = assistantService.authorizeConversation(conversationUuid).conversation();
        AssistantMessage message = conversationService.findMessage(conversation, messageUuid)
                .filter(m -> m.getSender() == MessageSender.ASSISTANT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No assistant message with uuid " + messageUuid + " in this conversation"));
        try {
            return textToSpeech.synthesize(message.getContent(), conversation.getLanguageCode());
        } catch (SpeechException e) {
            if (e.reason() == SpeechException.Reason.UNSUPPORTED_LANGUAGE) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, LANGUAGE_NOT_SPOKEN);
            }
            throw unavailable("Speech", e);
        }
    }

    /** A friendly 503; the provider's details stay in the log, and only as a reason. */
    private static ResponseStatusException unavailable(String what, SpeechException e) {
        if (e.reason() == SpeechException.Reason.NOT_CONFIGURED) {
            return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, VOICE_NOT_SET_UP);
        }
        log.warn("{} failed: {}", what, e.reason());
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, VOICE_UNAVAILABLE);
    }
}
