package com.magizhchi.share.repository;

import com.magizhchi.share.model.UserFilePin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UserFilePinRepository extends JpaRepository<UserFilePin, Long> {

    Optional<UserFilePin> findByUserIdAndFileMessageId(Long userId, Long fileMessageId);

    boolean existsByUserIdAndFileMessageId(Long userId, Long fileMessageId);

    /** All file IDs pinned by the user in a specific conversation */
    @Query("SELECT p.fileMessage.id FROM UserFilePin p " +
           "WHERE p.user.id = :userId AND p.fileMessage.conversation.id = :convId")
    Set<Long> findPinnedFileIdsByConversation(@Param("userId") Long userId,
                                               @Param("convId") Long conversationId);

    /** All pinned file messages for a user in a conversation */
    @Query("SELECT p FROM UserFilePin p " +
           "WHERE p.user.id = :userId AND p.fileMessage.conversation.id = :convId " +
           "AND p.fileMessage.isDeleted = false ORDER BY p.pinnedAt DESC")
    List<UserFilePin> findPinnedInConversation(@Param("userId") Long userId,
                                                @Param("convId") Long conversationId);
}
