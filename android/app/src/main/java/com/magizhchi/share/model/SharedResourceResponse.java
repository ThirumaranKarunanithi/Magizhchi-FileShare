package com.magizhchi.share.model;

import com.google.gson.annotations.SerializedName;

/**
 * Mirrors the backend's SharedResourceResponse. Returned by
 * {@code GET /api/share/shared-with-me} and the related share-list endpoints.
 *
 * <p>Note: this is <strong>different</strong> from {@link FileMessageResponse}.
 * The backend has separate DTOs because a share is not just a file — it's a
 * triple of (file, owner, target) plus permission/expiry metadata. The
 * Android client used to deserialize the response into FileMessageResponse,
 * which caused every field with a different name (sizeBytes vs fileSizeBytes,
 * fileName vs originalFileName, ownerName vs senderName, …) to silently
 * deserialize as 0 / null. That was the root cause of "0 B" sizes and
 * "From Someone" labels in the Shared Files surfaces.
 */
public class SharedResourceResponse {

    /** SharedResource row id — used for revoke. */
    @SerializedName("id")
    private String id;

    // ── File info ───────────────────────────────────────────────────────────

    @SerializedName("fileMessageId")
    private String fileMessageId;

    @SerializedName("fileName")
    private String fileName;

    @SerializedName("contentType")
    private String contentType;

    @SerializedName("category")
    private String category;

    @SerializedName("sizeBytes")
    private long sizeBytes;

    @SerializedName("folderPath")
    private String folderPath;

    /** When the file itself was originally uploaded. */
    @SerializedName("fileSentAt")
    private String fileSentAt;

    // ── Share metadata ──────────────────────────────────────────────────────

    /** USER | GROUP — whether this was shared directly or via a group. */
    @SerializedName("shareType")
    private String shareType;

    /** VIEWER | EDITOR — what the recipient is allowed to do. */
    @SerializedName("permission")
    private String permission;

    /** When the share was created (≠ when the file was uploaded). */
    @SerializedName("sharedAt")
    private String sharedAt;

    @SerializedName("expiresAt")
    private String expiresAt;

    // ── Owner (the person who shared it with me) ──────────────────────────

    @SerializedName("ownerId")
    private String ownerId;

    @SerializedName("ownerName")
    private String ownerName;

    @SerializedName("ownerPhotoUrl")
    private String ownerPhotoUrl;

    // ── Target (who it was shared with — usually the current user) ────────

    @SerializedName("targetId")
    private String targetId;

    @SerializedName("targetName")
    private String targetName;

    @SerializedName("targetPhotoUrl")
    private String targetPhotoUrl;

    public SharedResourceResponse() {}

    public String getId() { return id; }
    public String getFileMessageId() { return fileMessageId; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public String getCategory() { return category; }
    public long getSizeBytes() { return sizeBytes; }
    public String getFolderPath() { return folderPath; }
    public String getFileSentAt() { return fileSentAt; }
    public String getShareType() { return shareType; }
    public String getPermission() { return permission; }
    public String getSharedAt() { return sharedAt; }
    public String getExpiresAt() { return expiresAt; }
    public String getOwnerId() { return ownerId; }
    public String getOwnerName() { return ownerName; }
    public String getOwnerPhotoUrl() { return ownerPhotoUrl; }
    public String getTargetId() { return targetId; }
    public String getTargetName() { return targetName; }
    public String getTargetPhotoUrl() { return targetPhotoUrl; }
}
