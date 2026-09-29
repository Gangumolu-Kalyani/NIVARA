package com.sih.nivara.assistant.repository;

import com.sih.nivara.assistant.entity.AssistantConversation;
import com.sih.nivara.entity.AppUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Access to {@link AssistantConversation} records (table assistant_conversations).
 *
 * <p>Every lookup names the owner as well as the uuid: a conversation is only ever found for the
 * account that owns it, so there is no finder that could hand one to anybody else.
 */
@Repository
public interface AssistantConversationRepository extends JpaRepository<AssistantConversation, Long> {

    @EntityGraph(attributePaths = {"owner", "patient"})
    Optional<AssistantConversation> findByUuidAndOwner(UUID uuid, AppUser owner);

    /** The owner's conversations, most recently active first (ix_assistant_conversations_owner_updated). */
    @EntityGraph(attributePaths = {"owner", "patient"})
    List<AssistantConversation> findByOwnerOrderByUpdatedAtDescIdDesc(AppUser owner);
}
