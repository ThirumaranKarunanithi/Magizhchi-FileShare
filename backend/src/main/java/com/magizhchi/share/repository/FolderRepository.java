package com.magizhchi.share.repository;

import com.magizhchi.share.model.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FolderRepository extends JpaRepository<Folder, Long> {

    /** Root-level folders in a conversation (parent is null) */
    @Query("SELECT f FROM Folder f WHERE f.conversation.id = :convId AND f.parent IS NULL AND f.isDeleted = false")
    List<Folder> findRootFolders(@Param("convId") Long conversationId);

    /** Child folders of a given parent */
    @Query("SELECT f FROM Folder f WHERE f.conversation.id = :convId AND f.parent.id = :parentId AND f.isDeleted = false")
    List<Folder> findChildFolders(@Param("convId") Long conversationId, @Param("parentId") Long parentId);

    /** All non-deleted folders in a conversation */
    @Query("SELECT f FROM Folder f WHERE f.conversation.id = :convId AND f.isDeleted = false ORDER BY f.createdAt")
    List<Folder> findAllByConversationId(@Param("convId") Long conversationId);
}
