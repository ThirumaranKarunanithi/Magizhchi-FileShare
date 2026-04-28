package com.magizhchi.share.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Per-user pin on a folder. Folder rows with a matching row here surface in
 * the "Pinned" section at the top of the chat window for that user only.
 */
@Entity
@Table(name = "user_folder_pins",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "folder_id"}),
    indexes = @Index(name = "idx_folder_pin_user", columnList = "user_id")
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFolderPin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id", nullable = false)
    private Folder folder;

    @Column(nullable = false)
    @Builder.Default
    private Instant pinnedAt = Instant.now();
}
