package com.magizhchi.share.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "otp_codes", indexes = {
    @Index(name = "idx_otp_identifier", columnList = "identifier"),
    @Index(name = "idx_otp_expires",    columnList = "expiresAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mobile number or email address */
    @Column(nullable = false, length = 120)
    private String identifier;

    @Column(nullable = false, length = 10)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OtpPurpose purpose;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isUsed = false;

    @Column(nullable = false)
    private Instant createdAt;

    public enum OtpPurpose {
        REGISTRATION, LOGIN, PASSWORD_RESET
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isValid(String input) {
        return !isUsed && !isExpired() && code.equals(input);
    }
}
