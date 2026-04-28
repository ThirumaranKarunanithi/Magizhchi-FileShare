package com.magizhchi.share.model;

import com.google.gson.annotations.SerializedName;

public class ConversationResponse {

    @SerializedName("id")
    private String id;

    @SerializedName("type")
    private String type; // "DIRECT" or "GROUP"

    @SerializedName("name")
    private String name;

    @SerializedName("iconUrl")
    private String iconUrl;

    @SerializedName("memberCount")
    private int memberCount;

    @SerializedName("otherUserId")
    private String otherUserId;

    @SerializedName("lastFile")
    private FileMessageResponse lastFile;

    @SerializedName("createdAt")
    private String createdAt;

    public ConversationResponse() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }

    public int getMemberCount() { return memberCount; }
    public void setMemberCount(int memberCount) { this.memberCount = memberCount; }

    public String getOtherUserId() { return otherUserId; }
    public void setOtherUserId(String otherUserId) { this.otherUserId = otherUserId; }

    public FileMessageResponse getLastFile() { return lastFile; }
    public void setLastFile(FileMessageResponse lastFile) { this.lastFile = lastFile; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
