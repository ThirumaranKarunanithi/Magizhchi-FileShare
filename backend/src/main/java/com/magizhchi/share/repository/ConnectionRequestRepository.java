package com.magizhchi.share.repository;

import com.magizhchi.share.model.ConnectionRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConnectionRequestRepository extends JpaRepository<ConnectionRequest, Long> {

    /** Any request from sender → receiver regardless of status. */
    Optional<ConnectionRequest> findBySenderIdAndReceiverId(Long senderId, Long receiverId);

    /** All PENDING requests received by this user (inbox). */
    List<ConnectionRequest> findByReceiverIdAndStatusOrderByCreatedAtDesc(
            Long receiverId, ConnectionRequest.RequestStatus status);

    /** All PENDING requests sent by this user (outbox). */
    List<ConnectionRequest> findBySenderIdAndStatusOrderByCreatedAtDesc(
            Long senderId, ConnectionRequest.RequestStatus status);

    /** True if an ACCEPTED connection exists in either direction. */
    @Query("""
        SELECT COUNT(cr) > 0 FROM ConnectionRequest cr
        WHERE cr.status = 'ACCEPTED'
          AND ((cr.sender.id = :u1 AND cr.receiver.id = :u2)
            OR (cr.sender.id = :u2 AND cr.receiver.id = :u1))
        """)
    boolean isConnected(@Param("u1") Long userId1, @Param("u2") Long userId2);

    /** Most-recent request between two users in any direction (for status lookup). */
    @Query("""
        SELECT cr FROM ConnectionRequest cr
        WHERE (cr.sender.id = :u1 AND cr.receiver.id = :u2)
           OR (cr.sender.id = :u2 AND cr.receiver.id = :u1)
        ORDER BY cr.createdAt DESC
        """)
    List<ConnectionRequest> findLatestByPair(@Param("u1") Long userId1, @Param("u2") Long userId2);

    /** Rate-limit guard: how many requests has this user sent since a given timestamp? */
    @Query("""
        SELECT COUNT(cr) FROM ConnectionRequest cr
        WHERE cr.sender.id = :senderId AND cr.createdAt >= :since
        """)
    long countRequestsSince(@Param("senderId") Long senderId, @Param("since") Instant since);
}
