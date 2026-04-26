package com.magizhchi.share.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "user_blocks",
       indexes = {
           @Index(name = "idx_block_blocker", columnList = "blocker_id"),
           @Index(name = "idx_block_blocked", columnList = "blocked_id")
       },
       uniqueConstraints = {
           @UniqueConstraint(name = "uq_block_pair", columnNames = {"blocker_id", "blocked_id"})
       })
@EntityListeners(AuditingEntityListener.class)
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocker_id", nullable = false)
    private User blocker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_id", nullable = false)
    private User blocked;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;
}
