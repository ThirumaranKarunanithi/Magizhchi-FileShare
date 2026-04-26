package com.magizhchi.share.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "conversations")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationType type;

    /** Null for DIRECT conversations; set for GROUP */
    @Column(length = 80)
    private String name;

    /** S3 key for group icon */
    @Column(length = 255)
    private String iconUrl;

    /** User who created this conversation */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ConversationMember> members = new ArrayList<>();

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sentAt DESC")
    @Builder.Default
    private List<FileMessage> messages = new ArrayList<>();

    public enum ConversationType {
        DIRECT, GROUP, PERSONAL
    }
}
