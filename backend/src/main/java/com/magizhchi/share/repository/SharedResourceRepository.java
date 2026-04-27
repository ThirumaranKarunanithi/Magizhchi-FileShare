package com.magizhchi.share.repository;

import com.magizhchi.share.model.SharedResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SharedResourceRepository extends JpaRepository<SharedResource, Long> {

    // ── Shared with me (direct user shares) ──────────────────────────────

    @Query("""
        SELECT sr FROM SharedResource sr
        JOIN FETCH sr.fileMessage fm
        JOIN FETCH sr.owner
        WHERE sr.targetUser.id = :userId
          AND sr.revoked = false
          AND (sr.expiresAt IS NULL OR sr.expiresAt > :now)
        ORDER BY sr.createdAt DESC
        """)
    List<SharedResource> findDirectSharesWithUser(@Param("userId") Long userId,
                                                  @Param("now") Instant now);

    // ── Shared with me (via group membership) ────────────────────────────

    @Query("""
        SELECT sr FROM SharedResource sr
        JOIN FETCH sr.fileMessage fm
        JOIN FETCH sr.owner
        WHERE sr.shareType = 'GROUP'
          AND sr.targetGroup.id IN (
              SELECT m.conversation.id FROM ConversationMember m
              WHERE m.user.id = :userId AND m.isActive = true
          )
          AND sr.revoked = false
          AND (sr.expiresAt IS NULL OR sr.expiresAt > :now)
        ORDER BY sr.createdAt DESC
        """)
    List<SharedResource> findGroupSharesWithUser(@Param("userId") Long userId,
                                                 @Param("now") Instant now);

    // ── Shared by me ─────────────────────────────────────────────────────

    @Query("""
        SELECT sr FROM SharedResource sr
        JOIN FETCH sr.fileMessage fm
        WHERE sr.owner.id = :ownerId
          AND sr.revoked = false
        ORDER BY sr.createdAt DESC
        """)
    List<SharedResource> findSharedByUser(@Param("ownerId") Long ownerId);

    // ── Duplicate check ───────────────────────────────────────────────────

    Optional<SharedResource> findByFileMessageIdAndTargetUserIdAndRevokedFalse(
            Long fileMessageId, Long targetUserId);

    Optional<SharedResource> findByFileMessageIdAndTargetGroupIdAndRevokedFalse(
            Long fileMessageId, Long targetGroupId);

    // ── Access check (for download permission) ───────────────────────────

    @Query("""
        SELECT COUNT(sr) > 0 FROM SharedResource sr
        WHERE sr.fileMessage.id = :fmId
          AND sr.revoked = false
          AND (sr.expiresAt IS NULL OR sr.expiresAt > :now)
          AND (
              (sr.shareType = 'USER'  AND sr.targetUser.id  = :userId)
           OR (sr.shareType = 'GROUP' AND sr.targetGroup.id IN (
                   SELECT m.conversation.id FROM ConversationMember m
                   WHERE m.user.id = :userId AND m.isActive = true
              ))
          )
        """)
    boolean hasAccess(@Param("fmId") Long fileMessageId,
                      @Param("userId") Long userId,
                      @Param("now") Instant now);
}
