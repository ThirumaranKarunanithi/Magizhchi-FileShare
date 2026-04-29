package com.magizhchi.share.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Holds a registration in flight — created when the user submits the
 * registration form (sendRegistrationOtp), consumed when the OTP is
 * successfully verified (verifyRegistrationOtp). On verification the
 * row is converted into a real {@link User} and deleted.
 *
 * <p>Why a separate table instead of an unverified User row? Each rolled-back
 * or abandoned registration would otherwise burn a {@code users.id} value
 * out of the IDENTITY sequence, leaving permanent gaps in the primary key.
 * Pending registrations live here, where their own sequence churning has
 * no impact on real user IDs. The user_id assigned at verification time
 * is the FIRST id allocated for this account, so the users table stays
 * dense.
 *
 * <p>The pending row is upserted by mobile or email — submitting the
 * registration form a second time before verifying replaces the previous
 * pending entry rather than creating a parallel one.
 */
@Entity
@Table(name = "pending_registrations", indexes = {
    @Index(name = "idx_pending_mobile", columnList = "mobileNumber", unique = true),
    @Index(name = "idx_pending_email",  columnList = "email",        unique = true),
    @Index(name = "idx_pending_expires", columnList = "expiresAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String mobileNumber;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(nullable = false, length = 80)
    private String displayName;

    @Column(nullable = false)
    private Instant createdAt;

    /**
     * Auto-cleanup checkpoint. Pending registrations that aren't verified
     * within this window are swept by {@link com.magizhchi.share.service.AuthService}
     * on a schedule. Doesn't have to match the OTP TTL exactly — we keep it
     * a little longer so users have time to retry an expired OTP without
     * re-entering their details.
     */
    @Column(nullable = false)
    private Instant expiresAt;
}
