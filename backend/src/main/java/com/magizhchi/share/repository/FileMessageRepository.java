package com.magizhchi.share.repository;

import com.magizhchi.share.model.FileMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FileMessageRepository extends JpaRepository<FileMessage, Long> {

    /** Paginated file history for a conversation, newest-first */
    @Query("SELECT fm FROM FileMessage fm WHERE fm.conversation.id = :cid AND fm.isDeleted = false ORDER BY fm.sentAt DESC")
    Page<FileMessage> findByConversationId(@Param("cid") Long conversationId, Pageable pageable);

    /** Latest message in a conversation (for sidebar preview) */
    @Query("SELECT fm FROM FileMessage fm WHERE fm.conversation.id = :cid AND fm.isDeleted = false ORDER BY fm.sentAt DESC LIMIT 1")
    Optional<FileMessage> findLatestInConversation(@Param("cid") Long conversationId);

    /** Total bytes used by a user's uploads */
    @Query("SELECT COALESCE(SUM(fm.fileSizeBytes), 0) FROM FileMessage fm WHERE fm.sender.id = :uid AND fm.isDeleted = false")
    Long sumFileSizeByUser(@Param("uid") Long userId);
}
