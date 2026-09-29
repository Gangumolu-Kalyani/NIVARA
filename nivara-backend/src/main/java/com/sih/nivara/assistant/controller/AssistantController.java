package com.sih.nivara.assistant.controller;

import com.sih.nivara.assistant.dto.mapper.AssistantMapper;
import com.sih.nivara.assistant.dto.request.ConversationStartRequest;
import com.sih.nivara.assistant.dto.request.MessageRequest;
import com.sih.nivara.assistant.dto.response.ConversationDetailResponse;
import com.sih.nivara.assistant.dto.response.ConversationResponse;
import com.sih.nivara.assistant.dto.response.MessageExchangeResponse;
import com.sih.nivara.assistant.entity.AssistantConversation;
import com.sih.nivara.assistant.service.AssistantService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * The NIVARA assistant API, for patients on a paired device and for caregivers.
 *
 * <p>SecurityConfig admits PATIENT, CAREGIVER and ADMIN accounts. Everything else is decided in
 * {@link AssistantService} from the access token: a conversation is only ever visible to the
 * account that started it, and its patient is re-checked on every request. Another account's
 * conversation answers 404, as if it did not exist.
 */
@RestController
@RequestMapping("/api/assistant/conversations")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    /**
     * Starts a conversation. 201 with its Location. A caregiver naming a patient they cannot reach
     * gets 404 (or 403 when their access is too low); a patient naming another patient gets 403.
     */
    @PostMapping
    public ResponseEntity<ConversationResponse> start(@Valid @RequestBody(required = false) ConversationStartRequest request) {
        AssistantConversation conversation = assistantService.startConversation(
                request == null ? null : request.patientUuid(),
                request == null ? null : request.languageCode());
        ConversationResponse body = AssistantMapper.toResponse(conversation);
        return ResponseEntity.created(URI.create("/api/assistant/conversations/" + body.uuid())).body(body);
    }

    /** The caller's conversations, most recently active first. */
    @GetMapping
    public List<ConversationResponse> list() {
        return assistantService.listConversations().stream().map(AssistantMapper::toResponse).toList();
    }

    /** One of the caller's conversations, with all its messages. */
    @GetMapping("/{conversationUuid}")
    public ConversationDetailResponse get(@PathVariable UUID conversationUuid) {
        return AssistantMapper.toDetailResponse(assistantService.getConversation(conversationUuid));
    }

    /** Says something to the assistant. 200 with the stored message and reply; 409 when closed. */
    @PostMapping("/{conversationUuid}/messages")
    public MessageExchangeResponse send(@PathVariable UUID conversationUuid,
                                        @Valid @RequestBody MessageRequest request) {
        return AssistantMapper.toExchangeResponse(assistantService.sendMessage(conversationUuid, request.content()));
    }

    /** Closes a conversation. It keeps its history; closing again changes nothing. */
    @PostMapping("/{conversationUuid}/close")
    public ConversationResponse close(@PathVariable UUID conversationUuid) {
        return AssistantMapper.toResponse(assistantService.closeConversation(conversationUuid));
    }
}
