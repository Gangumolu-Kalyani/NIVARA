package com.sih.nivara.assistant.entity;

import com.sih.nivara.assistant.entity.enums.MessageSender;
import com.sih.nivara.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One turn of an {@link AssistantConversation}: something the owner said, or the assistant's
 * reply. Maps table assistant_messages (V7). Append-only: no setters, never updated.
 */
@Entity
@Table(name = "assistant_messages")
public class AssistantMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    private AssistantConversation conversation;

    @Column(name = "sequence_number", nullable = false, updatable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender", nullable = false, length = 20, updatable = false)
    private MessageSender sender;

    /** Who wrote a USER message; null for the assistant. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_user_id", updatable = false)
    private AppUser senderUser;

    @Column(name = "content", nullable = false, updatable = false, columnDefinition = "text")
    private String content;

    /** What produced an ASSISTANT message, such as a model id; null for USER messages. */
    @Column(name = "generated_by", length = 100, updatable = false)
    private String generatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AssistantMessage() {
        // required by JPA
    }

    private AssistantMessage(AssistantConversation conversation, int sequenceNumber, MessageSender sender,
                             AppUser senderUser, String content, String generatedBy) {
        this.conversation = conversation;
        this.sequenceNumber = sequenceNumber;
        this.sender = sender;
        this.senderUser = senderUser;
        this.content = content;
        this.generatedBy = generatedBy;
    }

    public static AssistantMessage fromUser(AssistantConversation conversation, int sequenceNumber,
                                            AppUser senderUser, String content) {
        return new AssistantMessage(conversation, sequenceNumber, MessageSender.USER, senderUser, content, null);
    }

    public static AssistantMessage fromAssistant(AssistantConversation conversation, int sequenceNumber,
                                                 String content, String generatedBy) {
        return new AssistantMessage(conversation, sequenceNumber, MessageSender.ASSISTANT, null, content, generatedBy);
    }

    public Long getId() {
        return id;
    }

    public UUID getUuid() {
        return uuid;
    }

    public AssistantConversation getConversation() {
        return conversation;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public MessageSender getSender() {
        return sender;
    }

    public AppUser getSenderUser() {
        return senderUser;
    }

    public String getContent() {
        return content;
    }

    public String getGeneratedBy() {
        return generatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AssistantMessage that)) {
            return false;
        }
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}
