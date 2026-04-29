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

    /** WhatsApp-style "About me" status text — surfaced under the user name in the chat header. */
    @SerializedName("statusMessage")
    private String statusMessage;

    @SerializedName("mobileNumber")
    private String mobileNumber;

    @SerializedName("email")
    private String email;

    /**
     * Present when {@code connectionStatus} is PENDING_SENT or PENDING_RECEIVED —
     * the request id used to accept / reject / cancel without a separate
     * lookup. Null for every other status.
     */
    @SerializedName("connectionRequestId")
    private String connectionRequestId;

    public UserSearchResponse() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getProfilePhotoUrl() { return profilePhotoUrl; }
    public void setProfilePhotoUrl(String profilePhotoUrl) { this.profilePhotoUrl = profilePhotoUrl; }

    public String getConnectionStatus() { return connectionStatus; }
    public void setConnectionStatus(String connectionStatus) { this.connectionStatus = connectionStatus; }

    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }

    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getConnectionRequestId() { return connectionRequestId; }
    public void setConnectionRequestId(String connectionRequestId) { this.connectionRequestId = connectionRequestId; }
}
