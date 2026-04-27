package com.magizhchi.share.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class SharedResourceResponse {

    private Long   id;             // SharedResource ID (used for revoke)

    // ── File info ────────────────────────────────────────────────────────
    private Long   fileMessageId;
    private String fileName;
    private String contentType;
    private String category;
    private long   sizeBytes;
    private String folderPath;
    private Instant fileSentAt;    // when file was originally uploaded

    // ── Share metadata ───────────────────────────────────────────────────
    private String  shareType;     // USER | GROUP
    private String  permission;    // VIEWER | EDITOR
    private Instant sharedAt;
    private Instant expiresAt;

    // ── Owner (who shared it) ────────────────────────────────────────────
    private Long   ownerId;
    private String ownerName;
    private String ownerPhotoUrl;

    // ── Target (who it was shared with) ─────────────────────────────────
    private Long   targetId;
    private String targetName;
    private String targetPhotoUrl;
}
