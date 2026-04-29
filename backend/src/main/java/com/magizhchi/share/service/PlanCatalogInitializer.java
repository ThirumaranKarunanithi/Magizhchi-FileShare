package com.magizhchi.share.service;

import com.magizhchi.share.model.Plan;
import com.magizhchi.share.model.User;
import com.magizhchi.share.repository.PlanRepository;
import com.magizhchi.share.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds the plan catalog at boot and backfills {@code plan_id = FREE} on
 * legacy User rows that pre-date the catalog.
 *
 * <p>The seeder is idempotent — it inserts only the plans whose {@code code}
 * isn't already present, so re-running it on every boot is safe. Updating a
 * price requires editing the row directly in the DB (or extending this
 * initializer to update labels / prices when they change). The
 * {@code storage_bytes} value should never change for a code once it's in
 * production data — create a new code instead.
 *
 * <p>Pricing source: confirmed by the user on 2026-04-29:
 * <pre>
 *   FREE       5 GB     —          —
 *   PRO_100    100 GB   ₹99/mo     ₹999/yr
 *   PRO_500    500 GB   ₹199/mo    ₹1999/yr
 *   MAX_2TB    2 TB     ₹499/mo    (no yearly)
 * </pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlanCatalogInitializer implements CommandLineRunner {

    private static final long GB = 1024L * 1024L * 1024L;
    private static final long TB = 1024L * GB;

    private final PlanRepository planRepo;
    private final UserRepository userRepo;

    @Override
    @Transactional
    public void run(String... args) {
        ensurePlan(Plan.builder()
                .code("FREE").label("Free")
                .storageBytes(5L * GB)
                .monthlyPaise(null).yearlyPaise(null)
                .isDefault(true)
                .sortOrder(0)
                .build());

        ensurePlan(Plan.builder()
                .code("PRO_100").label("100 GB")
                .storageBytes(100L * GB)
                .monthlyPaise(9_900L).yearlyPaise(99_900L)
                .isDefault(false)
                .sortOrder(1)
                .build());

        ensurePlan(Plan.builder()
                .code("PRO_500").label("500 GB")
                .storageBytes(500L * GB)
                .monthlyPaise(19_900L).yearlyPaise(199_900L)
                .isDefault(false)
                .sortOrder(2)
                .build());

        ensurePlan(Plan.builder()
                .code("MAX_2TB").label("2 TB")
                .storageBytes(2L * TB)
                .monthlyPaise(49_900L).yearlyPaise(null)   // monthly-only for now
                .isDefault(false)
                .sortOrder(3)
                .build());

        backfillLegacyUsers();
    }

    /** Insert the plan if its code isn't already present. */
    private void ensurePlan(Plan p) {
        planRepo.findByCode(p.getCode()).orElseGet(() -> {
            Plan saved = planRepo.save(p);
            log.info("Plan catalog: created '{}' ({} bytes, monthly={}, yearly={})",
                    saved.getCode(), saved.getStorageBytes(),
                    saved.getMonthlyPaise(), saved.getYearlyPaise());
            return saved;
        });
    }

    /**
     * Assign the default (FREE) plan to any User row that doesn't have one.
     * Touches existing data only; new accounts get FREE assigned at signup.
     */
    private void backfillLegacyUsers() {
        Plan free = planRepo.findFirstByIsDefaultTrue()
                .orElseThrow(() -> new IllegalStateException(
                        "No default plan found — catalog seeding failed."));

        List<User> users = userRepo.findAll();
        int patched = 0;
        for (User u : users) {
            if (u.getPlan() == null) {
                u.setPlan(free);
                // Don't blindly clobber maxStorageBytes — admins may have
                // granted overrides above the FREE limit. Only sync if the
                // current value is the legacy default.
                if (u.getMaxStorageBytes() != null
                        && u.getMaxStorageBytes() == 5L * GB) {
                    u.setMaxStorageBytes(free.getStorageBytes());
                }
                userRepo.save(u);
                patched++;
            }
        }
        if (patched > 0) {
            log.info("Plan catalog: backfilled {} legacy user(s) to FREE", patched);
        }
    }
}
