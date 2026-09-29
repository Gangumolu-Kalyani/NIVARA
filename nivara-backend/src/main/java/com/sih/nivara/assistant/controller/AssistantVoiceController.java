package com.sih.nivara.assistant.controller;

import com.sih.nivara.assistant.dto.response.TranscriptionResponse;
import com.sih.nivara.assistant.service.VoiceService;
import com.sih.nivara.assistant.speech.SpeechAudio;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Voice for the assistant, beside the unchanged text API in {@link AssistantController}.
 *
 * <p>Speaking to the assistant is two steps: transcribe a recording here, then send the transcript
 * as an ordinary message. Transcribing creates no message, so a failed or repeated recording never
 * adds anything to the conversation. Hearing a reply fetches speech for one assistant message.
 * Both answer only for the caller's own conversations; see {@link VoiceService}.
 *
 * <p>Errors from this controller carry a short message meant for the user, such as "I didn't catch
 * that", as {@code {status, message}}. They never contain provider details. The rest of the API
 * keeps Spring's default error body.
 */
@RestController
@RequestMapping("/api/assistant/conversations/{conversationUuid}")
public class AssistantVoiceController {

    private final VoiceService voiceService;

    public AssistantVoiceController(VoiceService voiceService) {
        this.voiceService = voiceService;
    }

    /** Transcribes a recording, sent as multipart field {@code audio}. Stores nothing. */
    @PostMapping(path = "/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TranscriptionResponse transcribe(@PathVariable UUID conversationUuid,
                                            @RequestPart(name = "audio", required = false) MultipartFile audio) {
        VoiceService.TranscriptionResult result = voiceService.transcribe(conversationUuid, audio);
        return new TranscriptionResponse(result.transcript(), result.languageCode(), result.detectedLanguageCode());
    }

    /** The status and the user-facing message of a voice error. */
    public record VoiceError(int status, String message) {
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<VoiceError> voiceError(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VoiceError(e.getStatusCode().value(), e.getReason()));
    }

    /** The spoken form of one assistant reply, as audio. Nothing is stored or cached. */
    @GetMapping("/messages/{messageUuid}/speech")
    public ResponseEntity<byte[]> speech(@PathVariable UUID conversationUuid, @PathVariable UUID messageUuid) {
        SpeechAudio audio = voiceService.speak(conversationUuid, messageUuid);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(audio.mediaType()))
                .cacheControl(CacheControl.noStore())
                .contentLength(audio.bytes().length)
                .body(audio.bytes());
    }
}
