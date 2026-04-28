package com.magizhchi.share.repository;

import com.magizhchi.share.model.UserFolderPin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Set;

public interface UserFolderPinRepository extends JpaRepository<UserFolderPin, Long> {

    Optional<UserFolderPin> findByUserIdAndFolderId(Long userId, Long folderId);

    boolean existsByUserIdAndFolderId(Long userId, Long folderId);

    /** All folder IDs pinned by the user in a specific conversation. */
    @Query("SELECT p.folder.id FROM UserFolderPin p " +
           "WHERE p.user.id = :userId AND p.folder.conversation.id = :convId " +
           "AND p.folder.isDeleted = false")
    Set<Long> findPinnedFolderIdsByConversation(@Param("userId") Long userId,
                                                 @Param("convId") Long conversationId);
}
