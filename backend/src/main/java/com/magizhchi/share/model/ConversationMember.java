package com.magizhchi.share.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "conversation_members",
       uniqueConstraints = @UniqueConstraint(columnNames = {"conversation_id", "user_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MemberRole role = MemberRole.MEMBER;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Builder.Default
    private Instant joinedAt = Instant.now();

    private Instant leftAt;

    /** The last message this member has read */
    private Long lastReadMessageId;

    public enum MemberRole {
        ADMIN, MEMBER
    }
}
