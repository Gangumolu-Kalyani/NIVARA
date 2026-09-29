package com.sih.nivara.assistant.service;

import com.sih.nivara.assistant.entity.AssistantConversation;
import com.sih.nivara.assistant.entity.AssistantMessage;
import com.sih.nivara.assistant.entity.enums.AssistantMode;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.service.PatientAccessService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The NIVARA assistant: conversations between an account and the assistant.
 *
 * <p>Every request goes through the same steps. The caller comes from the access token. Their
 * conversation is found by uuid and owner together, so another account's conversation is simply
 * not found (404). {@link AssistantContextResolver} then re-checks that the caller may still talk
 * about the conversation's patient. Only after that is anything read or written.
 *
 * <p>Sending a message stores it, asks {@link AssistantResponder} for the reply, and stores the
 * reply. The responder runs the language model and its tool calls outside any database
 * transaction, so a slow model holds no locks; and it always produces a reply, falling back to a
 * fixed one if the model fails, so the user's message never goes unanswered.
 *
 * <p>Not transactional itself: each step is its own short transaction in
 * {@link ConversationService}.
 */
@Service
public class AssistantService {

    /** How many recent messages a model sees, the new one included. */
    static final int HISTORY_LIMIT = 20;

    /** A conversation together with its messages, oldest first. */
    public record ConversationView(AssistantConversation conversation, List<AssistantMessage> messages) {
    }

    /** A conversation the caller owns and may still use, with the context resolved for it. */
    public record AuthorizedConversation(AssistantConversation conversation, AssistantContext context) {
    }

    /** One exchange: what the caller said and what the assistant answered. */
    public record Exchange(AssistantConversation conversation, AssistantMessage userMessage, AssistantMessage reply) {
    }

    private final AssistantContextResolver contextResolver;
    private final ConversationService conversationService;
    private final PatientAccessService patientAccessService;
    private final AssistantResponder responder;

    public AssistantService(AssistantContextResolver contextResolver,
                            ConversationService conversationService,
                            PatientAccessService patientAccessService,
                            AssistantResponder responder) {
        this.contextResolver = contextResolver;
        this.conversationService = conversationService;
        this.patientAccessService = patientAccessService;
        this.responder = responder;
    }

    /**
     * Starts a conversation for the caller. A caregiver may name a patient they can reach; a
     * patient always talks about themselves.
     */
    public AssistantConversation startConversation(UUID patientUuid, String languageCode) {
        AppUser caller = contextResolver.requireCaller();
        AssistantContext context = contextResolver.forNewConversation(caller, patientUuid, languageCode);
        AssistantConversation created = conversationService.create(context);
        return reload(created.getUuid(), caller);
    }

    /**
     * The caller's conversations, most recently active first. A caregiver no longer sees
     * conversations about a patient they have since lost access to.
     */
    public List<AssistantConversation> listConversations() {
        AppUser caller = contextResolver.requireCaller();
        List<AssistantConversation> owned = conversationService.findOwned(caller);
        if (owned.stream().noneMatch(c -> c.getMode() == AssistantMode.CAREGIVER && c.getPatient() != null)) {
            return owned;
        }
        Set<Long> reachable = patientAccessService.accessiblePatients().stream()
                .map(access -> access.patient().getId())
                .collect(Collectors.toSet());
        return owned.stream()
                .filter(c -> c.getMode() != AssistantMode.CAREGIVER
                        || c.getPatient() == null
                        || reachable.contains(c.getPatient().getId()))
                .toList();
    }

    /**
     * One of the caller's conversations, after exactly the checks every other assistant request
     * makes: the caller from the token, the conversation found by uuid and owner, and the
     * conversation's patient re-checked. 404 for anybody else's conversation, or one whose patient
     * the caller can no longer reach. For voice, which must authorize before any audio reaches a
     * speech provider.
     */
    public AuthorizedConversation authorizeConversation(UUID conversationUuid) {
        AppUser caller = contextResolver.requireCaller();
        AssistantConversation conversation = requireOwned(conversationUuid, caller);
        return new AuthorizedConversation(conversation, contextResolver.forConversation(caller, conversation));
    }

    /** One of the caller's conversations with all its messages; 404 for anybody else's. */
    public ConversationView getConversation(UUID conversationUuid) {
        AppUser caller = contextResolver.requireCaller();
        AssistantConversation conversation = requireOwned(conversationUuid, caller);
        contextResolver.forConversation(caller, conversation);
        return new ConversationView(conversation, conversationService.messages(conversation));
    }

    /**
     * Stores the caller's message and the assistant's reply. 404 for a conversation that is not
     * the caller's or whose patient they can no longer reach; 409 when it is closed.
     */
    public Exchange sendMessage(UUID conversationUuid, String content) {
        AppUser caller = contextResolver.requireCaller();
        AssistantConversation conversation = requireOwned(conversationUuid, caller);
        AssistantContext context = contextResolver.forConversation(caller, conversation);

        AssistantMessage userMessage = conversationService.appendUserMessage(conversation, caller, content.strip());

        AssistantResponder.Reply reply = responder.respond(context,
                conversationService.recentMessages(conversation, HISTORY_LIMIT));

        AssistantMessage assistantMessage = conversationService.appendAssistantMessage(
                conversation, reply.content(), reply.generatedBy());
        return new Exchange(reload(conversationUuid, caller), userMessage, assistantMessage);
    }

    /** Closes one of the caller's conversations; it keeps its history but takes no new messages. */
    public AssistantConversation closeConversation(UUID conversationUuid) {
        AppUser caller = contextResolver.requireCaller();
        AssistantConversation conversation = requireOwned(conversationUuid, caller);
        contextResolver.forConversation(caller, conversation);
        conversationService.close(conversation);
        return reload(conversationUuid, caller);
    }

    private AssistantConversation requireOwned(UUID conversationUuid, AppUser caller) {
        return conversationService.findOwned(conversationUuid, caller)
                .orElseThrow(() -> AssistantContextResolver.notFound(conversationUuid));
    }

    /** Reads the conversation again with its owner and patient fetched, for the response. */
    private AssistantConversation reload(UUID conversationUuid, AppUser caller) {
        return requireOwned(conversationUuid, caller);
    }
}
