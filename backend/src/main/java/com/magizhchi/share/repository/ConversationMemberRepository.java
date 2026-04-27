package com.magizhchi.share.repository;

import com.magizhchi.share.model.ConversationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, Long> {

    Optional<ConversationMember> findByConversationIdAndUserId(Long conversationId, Long userId);

    boolean existsByConversationIdAndUserIdAndIsActiveTrue(Long conversationId, Long userId);

    int countByConversationIdAndIsActiveTrue(Long conversationId);

    /** All active members of a group (used for WS share notifications). */
    @Query("""
        SELECT m FROM ConversationMember m
        JOIN FETCH m.user
        WHERE m.conversation.id = :convId AND m.isActive = true
        """)
    List<ConversationMember> findActiveMembers(@Param("convId") Long conversationId);
}
