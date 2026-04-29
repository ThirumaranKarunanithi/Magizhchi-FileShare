package com.magizhchi.share.dto.response;

import com.magizhchi.share.model.Plan;
import lombok.Builder;
import lombok.Data;

/**
 * API-shaped projection of a {@link Plan} row. Exposes only the fields the
 * client needs to render the upgrade picker / current plan badge — never
 * the raw DB id (clients reference plans by {@code code}).
 *
 * <p>Prices are in <strong>paise</strong>. Display formatting (₹X / mo,
 * ₹Y / yr) lives on the client.
 */
@Data
@Builder
public class PlanResponse {

    private String code;
    private String label;
    private long   storageBytes;

    /** {@code null} for free / non-priced tiers. */
    private Long monthlyPaise;

    /** {@code null} when only monthly is offered (e.g. MAX_2TB). */
    private Long yearlyPaise;

    private boolean isDefault;
    private int     sortOrder;

    public static PlanResponse from(Plan p) {
        if (p == null) return null;
        return PlanResponse.builder()
                .code(p.getCode())
                .label(p.getLabel())
                .storageBytes(p.getStorageBytes())
                .monthlyPaise(p.getMonthlyPaise())
                .yearlyPaise(p.getYearlyPaise())
                .isDefault(Boolean.TRUE.equals(p.getIsDefault()))
                .sortOrder(p.getSortOrder() == null ? 0 : p.getSortOrder())
                .build();
    }
}
