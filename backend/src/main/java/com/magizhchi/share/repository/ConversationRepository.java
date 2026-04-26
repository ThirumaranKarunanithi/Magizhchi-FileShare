package com.magizhchi.share.repository;

import com.magizhchi.share.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * All non-PERSONAL conversations where this user is an active member.
     * Personal storage is fetched via findPersonalConversation().
     */
    @Query("""
        SELECT DISTINCT c FROM Conversation c
        JOIN c.members m
        WHERE m.user.id = :userId AND m.isActive = true AND c.type <> 'PERSONAL'
        """)
    List<Conversation> findAllByMemberUserId(@Param("userId") Long userId);

    /** Find this user's PERSONAL storage conversation (at most one). */
    @Query("""
        SELECT c FROM Conversation c
        JOIN c.members m
        WHERE c.type = 'PERSONAL' AND m.user.id = :userId AND m.isActive = true
        """)
    Optional<Conversation> findPersonalConversation(@Param("userId") Long userId);

    /** Find existing DIRECT conversation between exactly two users */
    @Query("""
        SELECT c FROM Conversation c
        WHERE c.type = 'DIRECT'
          AND EXISTS (SELECT m1 FROM ConversationMember m1
                      WHERE m1.conversation = c AND m1.user.id = :u1 AND m1.isActive = true)
          AND EXISTS (SELECT m2 FROM ConversationMember m2
                      WHERE m2.conversation = c AND m2.user.id = :u2 AND m2.isActive = true)
        """)
    Optional<Conversation> findDirectConversation(
            @Param("u1") Long userId1, @Param("u2") Long userId2);
}
