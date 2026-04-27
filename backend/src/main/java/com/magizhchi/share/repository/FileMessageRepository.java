package com.magizhchi.share.repository;

import com.magizhchi.share.model.Conversation;
import com.magizhchi.share.model.FileMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FileMessageRepository extends JpaRepository<FileMessage, Long> {

    /** Paginated file history for a conversation, newest-first */
    @Query("SELECT fm FROM FileMessage fm WHERE fm.conversation.id = :cid AND fm.isDeleted = false ORDER BY fm.sentAt DESC")
    Page<FileMessage> findByConversationId(@Param("cid") Long conversationId, Pageable pageable);

    /** Latest message in a conversation (for sidebar preview) */
    @Query("SELECT fm FROM FileMessage fm WHERE fm.conversation.id = :cid AND fm.isDeleted = false ORDER BY fm.sentAt DESC LIMIT 1")
    Optional<FileMessage> findLatestInConversation(@Param("cid") Long conversationId);

    /** Total bytes used by ALL uploads from a user across all conversation types */
    @Query("SELECT COALESCE(SUM(fm.fileSizeBytes), 0) FROM FileMessage fm WHERE fm.sender.id = :uid AND fm.isDeleted = false")
    Long sumFileSizeByUser(@Param("uid") Long userId);

    /** Bytes used by a user within a specific conversation type (PERSONAL / DIRECT / GROUP) */
    @Query("SELECT COALESCE(SUM(fm.fileSizeBytes), 0) FROM FileMessage fm " +
           "WHERE fm.sender.id = :uid AND fm.conversation.type = :type AND fm.isDeleted = false")
    Long sumByUserAndConvType(@Param("uid") Long userId,
                              @Param("type") Conversation.ConversationType type);

    /** Bytes uploaded by a user inside one specific conversation */
    @Query("SELECT COALESCE(SUM(fm.fileSizeBytes), 0) FROM FileMessage fm " +
           "WHERE fm.sender.id = :uid AND fm.conversation.id = :cid AND fm.isDeleted = false")
    Long sumByUserInConversation(@Param("uid") Long userId, @Param("cid") Long convId);

    /** Top N largest files uploaded by a user (for breakdown view) */
    @Query("SELECT fm FROM FileMessage fm WHERE fm.sender.id = :uid AND fm.isDeleted = false ORDER BY fm.fileSizeBytes DESC")
    List<FileMessage> findTopFilesBySize(@Param("uid") Long userId, Pageable pageable);

    /**
     * Full-text file search for a user — returns files from every conversation
     * the user is an active member of, matching on filename OR caption.
     */
    @Query("""
        SELECT fm FROM FileMessage fm
        JOIN ConversationMember cm ON cm.conversation = fm.conversation
        WHERE cm.user.id = :uid
          AND cm.isActive = true
          AND fm.isDeleted = false
          AND (LOWER(fm.originalFileName) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(fm.caption)          LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY fm.sentAt DESC
        """)
    Page<FileMessage> searchForUser(@Param("uid") Long userId,
                                    @Param("q")   String query,
                                    Pageable pageable);
}
