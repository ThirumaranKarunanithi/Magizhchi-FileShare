package com.magizhchi.share.model;

import com.google.gson.annotations.SerializedName;

public class UserSearchResponse {

    @SerializedName("id")
    private String id;

    @SerializedName("displayName")
    private String displayName;

    @SerializedName("profilePhotoUrl")
    private String profilePhotoUrl;

    @SerializedName("connectionStatus")
    private String connectionStatus; // "CONNECTED", "PENDING", "NONE", etc.

    public UserSearchResponse() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getProfilePhotoUrl() { return profilePhotoUrl; }
    public void setProfilePhotoUrl(String profilePhotoUrl) { this.profilePhotoUrl = profilePhotoUrl; }

    public String getConnectionStatus() { return connectionStatus; }
    public void setConnectionStatus(String connectionStatus) { this.connectionStatus = connectionStatus; }
}
