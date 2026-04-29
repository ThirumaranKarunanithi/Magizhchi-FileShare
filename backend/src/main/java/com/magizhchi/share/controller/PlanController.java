package com.magizhchi.share.controller;

import com.magizhchi.share.dto.response.PlanResponse;
import com.magizhchi.share.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public catalog of storage plans. The list drives the upgrade picker on
 * both web and Android — having it as data lets the team change prices
 * without redeploying the app.
 *
 * <p>This endpoint intentionally does NOT require auth: the price card is
 * shown on the marketing surface (and to logged-out users browsing the
 * upgrade modal), so it lives outside the authenticated namespace.
 *
 * <p>The actual <em>upgrade</em> action — which would change a user's
 * {@code plan_id} and cause a billing transaction — is deliberately not
 * implemented yet; it requires a payment-provider integration that's
 * tracked separately.
 */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanRepository planRepo;

    @GetMapping
    public ResponseEntity<List<PlanResponse>> list() {
        return ResponseEntity.ok(planRepo.findAllByOrderBySortOrderAsc()
                .stream()
                .map(PlanResponse::from)
                .toList());
    }
}
