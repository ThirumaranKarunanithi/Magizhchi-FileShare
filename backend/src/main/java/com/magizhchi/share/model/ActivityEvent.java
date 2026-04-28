package com.magizhchi.share.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "activity_events", indexes = {
    @Index(name = "idx_activity_conv", columnList = "conversation_id, created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String eventType; // FILE_UPLOADED, FOLDER_CREATED, FILE_DELETED, FILE_PINNED, FOLDER_DELETED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    /** Optional — which file this event relates to */
    private Long fileMessageId;

    /** Optional — which folder this event relates to */
    private Long folderId;

    /** Human-readable description, e.g. "Alice uploaded report.pdf" */
    @Column(length = 300)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
