package com.magizhchi.share.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Ensures that columns added in later schema revisions exist and have no NULLs.
 *
 * Why this is needed:
 *   Hibernate ddl-auto=update cannot add a NOT NULL column to a non-empty table
 *   (PostgreSQL rejects "ALTER TABLE … ADD COLUMN … NOT NULL" with existing rows).
 *   Instead, we:
 *     1. Add the column with a DEFAULT (idempotent via IF NOT EXISTS — safe no-op if present).
 *     2. Back-fill any rows that still have NULL.
 *
 * Runs after the full application context is ready (post JPA init), so it is safe
 * to use JdbcTemplate directly.  Each statement is wrapped in its own try/catch so
 * a partial failure never prevents the app from serving requests.
 */
@Component
@Slf4j
public class DbMigrationRunner {

    private final JdbcTemplate jdbc;

    public DbMigrationRunner(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {

        // ── Step 1: Add missing columns (no-op if already present) ─────────────
        // Specifying DEFAULT lets PostgreSQL fill all pre-existing rows immediately.
        ddl("ALTER TABLE file_messages " +
            "ADD COLUMN IF NOT EXISTS download_permission VARCHAR(30) DEFAULT 'CAN_DOWNLOAD'");
        ddl("ALTER TABLE file_messages " +
            "ADD COLUMN IF NOT EXISTS is_pinned BOOLEAN DEFAULT FALSE");
        ddl("ALTER TABLE file_messages " +
            "ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN DEFAULT FALSE");
        ddl("ALTER TABLE file_messages " +
            "ADD COLUMN IF NOT EXISTS download_count INT DEFAULT 0");

        // ── Step 2: Back-fill any remaining NULLs ──────────────────────────────
        int p = dml("UPDATE file_messages SET download_permission = 'CAN_DOWNLOAD' WHERE download_permission IS NULL");
        int i = dml("UPDATE file_messages SET is_pinned   = FALSE WHERE is_pinned   IS NULL");
        int d = dml("UPDATE file_messages SET is_deleted  = FALSE WHERE is_deleted  IS NULL");
        int c = dml("UPDATE file_messages SET download_count = 0  WHERE download_count IS NULL");

        if (p + i + d + c > 0) {
            log.info("[DbMigration] Patched {} download_permission, {} is_pinned, " +
                     "{} is_deleted, {} download_count rows.", p, i, d, c);
        } else {
            log.debug("[DbMigration] No backfill needed — all columns present and populated.");
        }
    }

    /** Execute a DDL statement; swallow errors (e.g. column already exists). */
    private void ddl(String sql) {
        try {
            jdbc.execute(sql);
        } catch (Exception e) {
            log.debug("[DbMigration] DDL skipped ({}): {}", sql.substring(0, Math.min(80, sql.length())), e.getMessage());
        }
    }

    /** Execute a DML statement; return rows affected (0 on error). */
    private int dml(String sql) {
        try {
            return jdbc.update(sql);
        } catch (Exception e) {
            log.warn("[DbMigration] DML failed: {} — {}", sql, e.getMessage());
            return 0;
        }
    }
}
