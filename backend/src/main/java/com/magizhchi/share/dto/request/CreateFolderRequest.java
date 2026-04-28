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
}
