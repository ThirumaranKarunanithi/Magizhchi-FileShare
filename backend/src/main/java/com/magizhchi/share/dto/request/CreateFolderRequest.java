package com.magizhchi.share.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateFolderRequest {
    @NotBlank
    private String name;
    @NotNull
    private Long   conversationId;
    /** Null → create at root level */
    private Long   parentFolderId;
    /**
     * Optional. One of CAN_DOWNLOAD | VIEW_ONLY | ADMIN_ONLY_DOWNLOAD.
     * Falls back to CAN_DOWNLOAD when omitted or invalid. Files uploaded
     * into this folder will use this as the default download permission.
     */
    private String defaultPermission;

    /** Optional free-text description shown on the folder header. */
    private String description;
}
