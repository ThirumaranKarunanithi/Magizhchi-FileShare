package com.magizhchi.share.repository;

import com.magizhchi.share.model.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BlockRepository extends JpaRepository<Block, Long> {

    Optional<Block> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    /** True if either user has blocked the other. */
    @Query("""
        SELECT COUNT(b) > 0 FROM Block b
        WHERE (b.blocker.id = :u1 AND b.blocked.id = :u2)
           OR (b.blocker.id = :u2 AND b.blocked.id = :u1)
        """)
    boolean isBlockedEitherWay(@Param("u1") Long userId1, @Param("u2") Long userId2);

    /** IDs of users that this user has blocked. Used to filter search results. */
    @Query("SELECT b.blocked.id FROM Block b WHERE b.blocker.id = :blockerId")
    Set<Long> findBlockedIdsByBlocker(@Param("blockerId") Long blockerId);

    /** IDs of users who have blocked this user (hidden from their search). */
    @Query("SELECT b.blocker.id FROM Block b WHERE b.blocked.id = :blockedId")
    Set<Long> findBlockerIdsByBlocked(@Param("blockedId") Long blockedId);

    /** Full block list for the "Blocked users" settings screen. */
    List<Block> findByBlockerIdOrderByCreatedAtDesc(Long blockerId);
}
