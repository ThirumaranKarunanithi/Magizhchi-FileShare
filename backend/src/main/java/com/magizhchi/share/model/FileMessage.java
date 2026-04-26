package com.magizhchi.share.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "file_messages", indexes = {
    @Index(name = "idx_fm_conversation", columnList = "conversation_id"),
    @Index(name = "idx_fm_sender",       columnList = "sender_id"),
    @Index(name = "idx_fm_sent_at",      columnList = "sentAt")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    // ── File metadata ───────────────────────────────────────────
    @Column(nullable = false, length = 255)
    private String originalFileName;

    @Column(nullable = false, length = 120)
    private String contentType;

    @Column(nullable = false)
    private Long fileSizeBytes;

    /** S3 object key (NOT a public URL) */
    @Column(nullable = false, length = 512)
    private String s3Key;

    /** S3 key for thumbnail (images/videos only) */
    @Column(length = 512)
    private String thumbnailKey;

    /** Optional text caption sent with the file */
    @Column(length = 500)
    private String caption;

    /**
     * Relative folder path when the file was uploaded as part of a folder upload.
     * e.g. "MyFolder/" or "MyFolder/subfolder/"
     * Null for individual file uploads.
     */
    @Column(length = 512)
    private String folderPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private FileCategory category = FileCategory.OTHER;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant sentAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    private Instant deletedAt;

    // ── Download tracking ───────────────────────────────────────
    @Column(nullable = false)
    @Builder.Default
    private Integer downloadCount = 0;

    public enum FileCategory {
        IMAGE, VIDEO, DOCUMENT, AUDIO, ARCHIVE, OTHER
    }

    /** Determines file category from MIME type */
    public static FileCategory categoryFrom(String mimeType) {
        if (mimeType == null) return FileCategory.OTHER;
        if (mimeType.startsWith("image/"))       return FileCategory.IMAGE;
        if (mimeType.startsWith("video/"))       return FileCategory.VIDEO;
        if (mimeType.startsWith("audio/"))       return FileCategory.AUDIO;
        if (mimeType.contains("pdf") || mimeType.contains("word") ||
            mimeType.contains("excel") || mimeType.contains("powerpoint") ||
            mimeType.contains("text"))            return FileCategory.DOCUMENT;
        if (mimeType.contains("zip") || mimeType.contains("rar") ||
            mimeType.contains("7z") || mimeType.contains("tar")) return FileCategory.ARCHIVE;
        return FileCategory.OTHER;
    }
}
