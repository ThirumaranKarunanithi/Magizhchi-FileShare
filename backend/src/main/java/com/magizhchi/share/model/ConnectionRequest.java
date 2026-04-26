package com.magizhchi.share.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "connection_requests",
       indexes = {
           @Index(name = "idx_cr_sender",   columnList = "sender_id"),
           @Index(name = "idx_cr_receiver", columnList = "receiver_id"),
           @Index(name = "idx_cr_status",   columnList = "status")
       },
       uniqueConstraints = {
           @UniqueConstraint(name = "uq_connection_pair", columnNames = {"sender_id", "receiver_id"})
       })
@EntityListeners(AuditingEntityListener.class)
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ConnectionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RequestStatus status = RequestStatus.PENDING;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    /** Set when the receiver responds (accept / reject) or sender cancels. */
    private Instant respondedAt;

    public enum RequestStatus {
        PENDING, ACCEPTED, REJECTED, CANCELLED
    }
}
