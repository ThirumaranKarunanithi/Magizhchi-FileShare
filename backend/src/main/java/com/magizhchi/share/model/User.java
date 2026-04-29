package com.magizhchi.share.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_mobile", columnList = "mobileNumber", unique = true),
    @Index(name = "idx_user_email",  columnList = "email")
})
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 20)
    private String mobileNumber;

    @Column(unique = true, length = 120)
    private String email;

    @Column(nullable = false, length = 80)
    private String displayName;

    @Column(columnDefinition = "TEXT")   // presigned S3 URLs regularly exceed varchar(255)
    private String profilePhotoUrl;

    @Column(length = 140)
    private String statusMessage;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    private Instant lastSeenAt;

    /** Bytes consumed by all files uploaded by this user (personal + direct + groups). */
    @Column(nullable = false)
    @Builder.Default
    private Long storageUsedBytes = 0L;

    /**
     * Per-user storage cap in bytes. Default 5 GB (free plan).
     *
     * <p>Kept in sync with {@link #plan}'s {@code storageBytes} whenever the
     * user upgrades / downgrades, so existing enforcement code that reads
     * this column directly continues to work without a change. It also
     * doubles as a per-user override for staff / admin grants — if you
     * want to give a specific user extra room without changing their
     * subscription, bump this column.
     */
    @Column(nullable = false)
    @Builder.Default
    private Long maxStorageBytes = 5_368_709_120L;   // 5 * 1024^3

    /**
     * Subscription plan the user is on. Null for legacy rows created before
     * the plan catalog existed — {@code PlanCatalogInitializer} backfills
     * those to FREE on first boot. New accounts default to FREE in
     * {@code AuthService.verifyRegistrationOtp()}.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Plan plan;

    /** One-directional: contacts this user has added */
    @JsonIgnore            // prevent LazyInitializationException during Jackson serialization
    @ToString.Exclude      // prevent LazyInitializationException in Lombok toString() → Spring Security getName()
    @EqualsAndHashCode.Exclude  // prevent infinite recursion in equals/hashCode
    @ManyToMany
    @JoinTable(
        name = "user_contacts",
        joinColumns        = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "contact_id")
    )
    @Builder.Default
    private Set<User> contacts = new HashSet<>();
}
