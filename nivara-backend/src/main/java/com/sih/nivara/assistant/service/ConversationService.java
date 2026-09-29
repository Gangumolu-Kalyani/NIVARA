package com.sih.nivara.assistant.service;

import com.sih.nivara.assistant.entity.AssistantConversation;
import com.sih.nivara.assistant.entity.AssistantMessage;
import com.sih.nivara.assistant.repository.AssistantConversationRepository;
import com.sih.nivara.assistant.repository.AssistantMessageRepository;
import com.sih.nivara.entity.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage for assistant conversations and their messages, in short transactions.
 *
 * <p>Deliberately knows nothing about authorization: {@link AssistantService} only calls it with a
 * conversation it has already found for its owner and re-checked. Appending a message locks the
 * conversation row, so messages sent at the same moment still get distinct, consecutive sequence
 * numbers.
 */
@Service
@Transactional(readOnly = true)
public class ConversationService {

    private final AssistantConversationRepository conversationRepository;
    private final AssistantMessageRepository messageRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public ConversationService(AssistantConversationRepository conversationRepository,
                               AssistantMessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public AssistantConversation create(AssistantContext context) {
        return conversationRepository.save(new AssistantConversation(
                context.caller(), context.patient(), context.mode(), context.languageCode()));
    }

    /** The owner's conversation with this uuid; empty for anybody else's. */
    public Optional<AssistantConversation> findOwned(UUID uuid, AppUser owner) {
        return conversationRepository.findByUuidAndOwner(uuid, owner);
    }

    /** The owner's conversations, most recently active first. */
    public List<AssistantConversation> findOwned(AppUser owner) {
        return conversationRepository.findByOwnerOrderByUpdatedAtDescIdDesc(owner);
    }

    /** Every message of the conversation, oldest first. */
    public List<AssistantMessage> messages(AssistantConversation conversation) {
        return messageRepository.findByConversationOrderBySequenceNumberAsc(conversation);
    }

    /** The latest messages, at most limit of them, oldest first. */
    public List<AssistantMessage> recentMessages(AssistantConversation conversation, int limit) {
        List<AssistantMessage> newestFirst = new ArrayList<>(
                messageRepository.findByConversationOrderBySequenceNumberDesc(conversation, PageRequest.of(0, limit)));
        Collections.reverse(newestFirst);
        return newestFirst;
    }

    /** Appends what the owner said. 409 when the conversation has been closed. */
    @Transactional
    public AssistantMessage appendUserMessage(AssistantConversation conversation, AppUser sender, String content) {
        AssistantConversation locked = lock(conversation);
        if (!locked.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This conversation is closed");
        }
        return append(locked, AssistantMessage.fromUser(locked, nextSequence(locked), sender, content));
    }

    /**
     * Appends the assistant's reply. Recorded even if the conversation was closed while the reply
     * was being produced, so every question keeps its answer.
     */
    @Transactional
    public AssistantMessage appendAssistantMessage(AssistantConversation conversation, String content,
                                                   String generatedBy) {
        AssistantConversation locked = lock(conversation);
        return append(locked, AssistantMessage.fromAssistant(locked, nextSequence(locked), content, generatedBy));
    }

    /** Closes the conversation; closing it again changes nothing. */
    @Transactional
    public AssistantConversation close(AssistantConversation conversation) {
        AssistantConversation locked = lock(conversation);
        locked.close(Instant.now());
        return locked;
    }

    private AssistantMessage append(AssistantConversation locked, AssistantMessage message) {
        AssistantMessage saved = messageRepository.save(message);
        locked.touch(Instant.now());
        return saved;
    }

    private int nextSequence(AssistantConversation locked) {
        return messageRepository.maxSequenceNumber(locked) + 1;
    }

    /** The conversation, managed in this transaction, its row locked and freshly read. */
    private AssistantConversation lock(AssistantConversation conversation) {
        AssistantConversation managed = entityManager.contains(conversation)
                ? conversation
                : entityManager.find(AssistantConversation.class, conversation.getId());
        entityManager.refresh(managed, LockModeType.PESSIMISTIC_WRITE);
        return managed;
    }
}
