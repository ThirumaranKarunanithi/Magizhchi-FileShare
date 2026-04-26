package com.magizhchi.share.repository;

import com.magizhchi.share.model.ConversationMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, Long> {

    Optional<ConversationMember> findByConversationIdAndUserId(Long conversationId, Long userId);

    boolean existsByConversationIdAndUserIdAndIsActiveTrue(Long conversationId, Long userId);

    int countByConversationIdAndIsActiveTrue(Long conversationId);
}
