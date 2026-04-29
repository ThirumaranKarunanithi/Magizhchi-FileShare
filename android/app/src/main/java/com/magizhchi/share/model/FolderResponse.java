package com.magizhchi.share.model;

import com.google.gson.annotations.SerializedName;

/**
 * Server-side folder summary, returned by POST /api/folders and GET
 * /api/folders.
 */
public class FolderResponse {

    @SerializedName("id")               private Long   id;
    @SerializedName("name")             private String name;
    @SerializedName("description")      private String description;
    @SerializedName("parentId")         private Long   parentId;
    @SerializedName("conversationId")   private Long   conversationId;
    @SerializedName("createdById")      private Long   createdById;
    @SerializedName("createdByName")    private String createdByName;
    @SerializedName("createdAt")        private String createdAt;
    @SerializedName("defaultPermission") private String defaultPermission;
    @SerializedName("pinned")           private boolean pinned;

    public Long   getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Long   getParentId() { return parentId; }
    public Long   getConversationId() { return conversationId; }
    public Long   getCreatedById() { return createdById; }
    public String getCreatedByName() { return createdByName; }
    public String getCreatedAt() { return createdAt; }
    public String getDefaultPermission() { return defaultPermission; }
    public boolean isPinned() { return pinned; }
}
