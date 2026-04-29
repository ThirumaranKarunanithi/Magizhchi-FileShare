package com.magizhchi.share.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Mirrors the backend's StorageResponse — full per-section breakdown of the
 * caller's storage usage. The Storage Usage popup uses every field; the home
 * screen only reads usedBytes / limitBytes / usedPercent for the progress bar.
 */
public class StorageUsageResponse {

    @SerializedName("usedBytes")
    private long usedBytes;

    @SerializedName("limitBytes")
    private long limitBytes;

    @SerializedName("usedPercent")
    private double usedPercent;

    @SerializedName("personalBytes")
    private long personalBytes;

    @SerializedName("directBytes")
    private long directBytes;

    @SerializedName("groupBytes")
    private long groupBytes;

    @SerializedName("groupBreakdown")
    private List<GroupItem> groupBreakdown;

    @SerializedName("topFiles")
    private List<TopFileItem> topFiles;

    public StorageUsageResponse() {}

    public long getUsedBytes() { return usedBytes; }
    public void setUsedBytes(long usedBytes) { this.usedBytes = usedBytes; }

    public long getLimitBytes() { return limitBytes; }
    public void setLimitBytes(long limitBytes) { this.limitBytes = limitBytes; }

    public double getUsedPercent() { return usedPercent; }
    public void setUsedPercent(double usedPercent) { this.usedPercent = usedPercent; }

    public long getPersonalBytes() { return personalBytes; }
    public void setPersonalBytes(long personalBytes) { this.personalBytes = personalBytes; }

    public long getDirectBytes() { return directBytes; }
    public void setDirectBytes(long directBytes) { this.directBytes = directBytes; }

    public long getGroupBytes() { return groupBytes; }
    public void setGroupBytes(long groupBytes) { this.groupBytes = groupBytes; }

    public List<GroupItem> getGroupBreakdown() { return groupBreakdown; }
    public void setGroupBreakdown(List<GroupItem> groupBreakdown) { this.groupBreakdown = groupBreakdown; }

    public List<TopFileItem> getTopFiles() { return topFiles; }
    public void setTopFiles(List<TopFileItem> topFiles) { this.topFiles = topFiles; }

    /** Per-group bytes (only groups where the user uploaded ≥ 1 file). */
    public static class GroupItem {
        @SerializedName("conversationId") private Long conversationId;
        @SerializedName("name")           private String name;
        @SerializedName("usedBytes")      private long usedBytes;

        public Long getConversationId() { return conversationId; }
        public String getName() { return name; }
        public long getUsedBytes() { return usedBytes; }
    }

    /** Top-N largest files uploaded by the user. */
    public static class TopFileItem {
        @SerializedName("id")          private Long id;
        @SerializedName("fileName")    private String fileName;
        @SerializedName("contentType") private String contentType;
        @SerializedName("category")    private String category;
        @SerializedName("sizeBytes")   private long sizeBytes;
        @SerializedName("sentAt")      private String sentAt;

        public Long getId() { return id; }
        public String getFileName() { return fileName; }
        public String getCategory() { return category; }
        public long getSizeBytes() { return sizeBytes; }
        public String getSentAt() { return sentAt; }
    }
}
