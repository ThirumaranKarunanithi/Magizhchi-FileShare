package com.magizhchi.share.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ShareRequest {

    /** IDs of FileMessage records to share */
    @NotEmpty(message = "At least one file must be selected.")
    private List<Long> resourceIds;

    /** "USER" or "GROUP" */
    @NotNull
    private String shareType;

    /** User ID or Conversation (group) ID depending on shareType */
    @NotNull
    private Long targetId;

    /** "VIEWER" or "EDITOR" */
    @NotNull
    private String permission;
}
