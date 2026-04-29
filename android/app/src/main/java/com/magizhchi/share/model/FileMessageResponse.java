package com.magizhchi.share.model;

import com.google.gson.annotations.SerializedName;

public class FileMessageResponse {

    @SerializedName("id")
    private String id;

    @SerializedName("conversationId")
    private String conversationId;

    @SerializedName("senderId")
    private String senderId;

    @SerializedName("senderName")
    private String senderName;

    @SerializedName("originalFileName")
    private String originalFileName;

    @SerializedName("contentType")
    private String contentType;

    @SerializedName("fileSizeBytes")
    private long fileSizeBytes;

    @SerializedName("category")
    private String category;

    @SerializedName("caption")
    private String caption;

    @SerializedName("folderPath")
    private String folderPath;

    @SerializedName("sentAt")
    private String sentAt;

    @SerializedName("isDeleted")
    private boolean isDeleted;

    @SerializedName("conversationName")
    private String conversationName;

    @SerializedName("downloadPermission")
    private String downloadPermission;

    public FileMessageResponse() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }

    public String getFolderPath() { return folderPath; }
    public void setFolderPath(String folderPath) { this.folderPath = folderPath; }

    public String getSentAt() { return sentAt; }
    public void setSentAt(String sentAt) { this.sentAt = sentAt; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public String getConversationName() { return conversationName; }
    public void setConversationName(String conversationName) { this.conversationName = conversationName; }

    public String getDownloadPermission() { return downloadPermission; }
    public void setDownloadPermission(String downloadPermission) { this.downloadPermission = downloadPermission; }
}
