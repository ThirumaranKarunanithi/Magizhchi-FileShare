package com.magizhchi.share.model;

import com.google.gson.annotations.SerializedName;

public class GroupMemberResponse {

    @SerializedName("userId")
    private String userId;

    @SerializedName("displayName")
    private String displayName;

    @SerializedName("profilePhotoUrl")
    private String profilePhotoUrl;

    @SerializedName("role")
    private String role; // "ADMIN" or "MEMBER"

    @SerializedName("joinedAt")
    private String joinedAt;

    public GroupMemberResponse() {}

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getProfilePhotoUrl() { return profilePhotoUrl; }
    public void setProfilePhotoUrl(String profilePhotoUrl) { this.profilePhotoUrl = profilePhotoUrl; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getJoinedAt() { return joinedAt; }
    public void setJoinedAt(String joinedAt) { this.joinedAt = joinedAt; }
}
