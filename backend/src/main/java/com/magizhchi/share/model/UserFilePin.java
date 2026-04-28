package com.magizhchi.share.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "user_file_pins",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "file_message_id"}),
    indexes = @Index(name = "idx_pin_user_conv", columnList = "user_id")
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFilePin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_message_id", nullable = false)
    private FileMessage fileMessage;

    @Column(nullable = false)
    @Builder.Default
    private Instant pinnedAt = Instant.now();
}
