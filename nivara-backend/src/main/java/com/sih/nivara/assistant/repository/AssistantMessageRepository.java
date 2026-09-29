package com.sih.nivara.assistant.repository;

import com.sih.nivara.assistant.entity.AssistantConversation;
import com.sih.nivara.assistant.entity.AssistantMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Access to {@link AssistantMessage} records (table assistant_messages). Ordering follows the
 * unique constraint uq_assistant_messages_sequence.
 */
@Repository
public interface AssistantMessageRepository extends JpaRepository<AssistantMessage, Long> {

    /** The whole conversation, oldest first. */
    @EntityGraph(attributePaths = "senderUser")
    List<AssistantMessage> findByConversationOrderBySequenceNumberAsc(AssistantConversation conversation);

    /** The latest messages, newest first; the page size bounds how much history is read. */
    @EntityGraph(attributePaths = "senderUser")
    List<AssistantMessage> findByConversationOrderBySequenceNumberDesc(AssistantConversation conversation,
                                                                       Pageable page);

    /** A message by uuid, only within this conversation, so no other conversation's message is ever found. */
    Optional<AssistantMessage> findByUuidAndConversation(UUID uuid, AssistantConversation conversation);

    /** The highest sequence number used so far, or 0 for an empty conversation. */
    @Query("select coalesce(max(m.sequenceNumber), 0) from AssistantMessage m where m.conversation = :conversation")
    int maxSequenceNumber(@Param("conversation") AssistantConversation conversation);
}
