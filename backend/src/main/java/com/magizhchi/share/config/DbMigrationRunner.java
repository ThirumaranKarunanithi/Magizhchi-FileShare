package com.magizhchi.share.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backfills NULL values in columns that were added after the initial schema was
 * created (ddl-auto=update adds the column but cannot set a default on existing rows).
 *
 * Safe to run on every startup — the WHERE clause limits actual work to rows that
 * still have NULL, so once all rows are patched the queries are no-ops.
 */
@Component
@Slf4j
public class DbMigrationRunner {

    @PersistenceContext
    private EntityManager em;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfillNullColumns() {
        int permFixed = em.createNativeQuery(
                "UPDATE file_messages SET download_permission = 'CAN_DOWNLOAD' " +
                "WHERE download_permission IS NULL")
                .executeUpdate();

        int pinnedFixed = em.createNativeQuery(
                "UPDATE file_messages SET is_pinned = false " +
                "WHERE is_pinned IS NULL")
                .executeUpdate();

        int deletedFixed = em.createNativeQuery(
                "UPDATE file_messages SET is_deleted = false " +
                "WHERE is_deleted IS NULL")
                .executeUpdate();

        int countFixed = em.createNativeQuery(
                "UPDATE file_messages SET download_count = 0 " +
                "WHERE download_count IS NULL")
                .executeUpdate();

        if (permFixed + pinnedFixed + deletedFixed + countFixed > 0) {
            log.info("[DbMigration] Backfilled {} download_permission, {} is_pinned, " +
                     "{} is_deleted, {} download_count NULL rows in file_messages.",
                     permFixed, pinnedFixed, deletedFixed, countFixed);
        }
    }
}
