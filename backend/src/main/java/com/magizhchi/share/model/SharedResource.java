package com.magizhchi.share.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Reference-based share record.
 * A file is stored once; this table grants access to additional users/groups.
 * Storage is NEVER increased for the recipient — only the original uploader pays.
 */
@Entity
@Table(name = "shared_resources", indexes = {
    @Index(name = "idx_share_target_user",  columnList = "target_user_id"),
    @Index(name = "idx_share_target_group", columnList = "target_group_id"),
    @Index(name = "idx_share_owner",        columnList = "owner_id"),
    @Index(name = "idx_share_file",         columnList = "file_message_id"),
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharedResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The file being shared — never duplicated, always referenced */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_message_id", nullable = false)
    private FileMessage fileMessage;

    /** User who created the share */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ShareType shareType;

    /** Set when shareType = USER */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id")
    private User targetUser;

    /** Set when shareType = GROUP */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_group_id")
    private Conversation targetGroup;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private Permission permission = Permission.VIEWER;

    @Column(nullable = false)
    @Builder.Default
    private Boolean revoked = false;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    private Instant revokedAt;

    /** Optional expiry — null means never expires */
    private Instant expiresAt;

    // ── Enums ──────────────────────────────────────────────────────────────

    public enum ShareType   { USER, GROUP }
    public enum Permission  { VIEWER, EDITOR }
}
