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
