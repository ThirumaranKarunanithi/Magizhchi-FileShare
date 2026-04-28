package com.magizhchi.share.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data @Builder
public class FileMessageResponse {
    private Long    id;
    private Long    conversationId;
    private Long    senderId;
    private String  senderName;
    private String  senderPhotoUrl;
    private String  originalFileName;
    private String  contentType;
    private Long    fileSizeBytes;
    private String  category;       // IMAGE | VIDEO | DOCUMENT | AUDIO | ARCHIVE | OTHER
    private String  caption;
    private String  folderPath;          // non-null when file came from a folder upload
    private Long    folderId;            // managed Folder entity id
    private String  conversationName;    // display name of the conversation (for search results)
    private boolean hasThumbnail;
    private String  downloadUrl;         // populated on-demand
    private String  thumbnailUrl;        // populated on-demand
    private Instant sentAt;
    // ── New fields ──────────────────────────────────────────────────────────────
    private String  downloadPermission;  // VIEW_ONLY | CAN_DOWNLOAD | ADMIN_ONLY_DOWNLOAD
    private boolean isPinned;            // pinned by the requesting user
}
