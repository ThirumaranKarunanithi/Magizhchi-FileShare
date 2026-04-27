package com.magizhchi.share.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class StorageResponse {

    /** Total bytes used by this user across all upload types */
    private long usedBytes;

    /** User's storage cap (default 5 GB) */
    private long limitBytes;

    /** usedBytes / limitBytes × 100, rounded to 1 decimal */
    private double usedPercent;

    /** Bytes from personal-storage uploads */
    private long personalBytes;

    /** Bytes from direct-chat uploads */
    private long directBytes;

    /** Bytes from group uploads */
    private long groupBytes;

    /** Per-group breakdown (only groups where this user uploaded at least one file) */
    private List<GroupItem> groupBreakdown;

    /** Top 10 largest files uploaded by this user */
    private List<TopFileItem> topFiles;

    // ── Nested DTOs ──────────────────────────────────────────────────────────

    @Data @Builder
    public static class GroupItem {
        private Long   conversationId;
        private String name;
        private long   usedBytes;
    }

    @Data @Builder
    public static class TopFileItem {
        private Long   id;
        private String fileName;
        private String contentType;
        private String category;
        private long   sizeBytes;
        private Instant sentAt;
    }
}
