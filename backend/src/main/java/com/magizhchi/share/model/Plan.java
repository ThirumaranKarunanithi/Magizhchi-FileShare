package com.magizhchi.share.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Storage plan catalog. Seeded at boot by {@code PlanCatalogInitializer}.
 *
 * <p>Pricing is stored in <strong>paise</strong> (₹1 = 100 paise) so we never
 * round half a rupee on display or arithmetic. Both monthly and yearly are
 * nullable: the FREE tier has neither, the highest tier (2 TB) is monthly-only
 * for now.
 *
 * <p>The {@code code} column is the stable identifier referenced from
 * {@link User#getPlan()} and from any future billing integration — change
 * the {@code label} freely, but never the {@code code} once it's in
 * production data.
 */
@Entity
@Table(name = "plans", indexes = {
    @Index(name = "idx_plan_code", columnList = "code", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable identifier — "FREE", "PRO_100", "PRO_500", "MAX_2TB". */
    @Column(nullable = false, unique = true, length = 32)
    private String code;

    /** Display label — "Free", "100 GB", "500 GB", "2 TB". */
    @Column(nullable = false, length = 64)
    private String label;

    /** Storage allowance in bytes. */
    @Column(nullable = false)
    private Long storageBytes;

    /** Monthly price in paise. {@code null} for the FREE tier. */
    @Column
    private Long monthlyPaise;

    /** Yearly price in paise. {@code null} when only monthly is offered. */
    @Column
    private Long yearlyPaise;

    /** True for the plan assigned to brand-new accounts (FREE). Exactly one row should be true. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    /** Display order in the upgrade picker. Lower = shown first. */
    @Column(nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;
}
