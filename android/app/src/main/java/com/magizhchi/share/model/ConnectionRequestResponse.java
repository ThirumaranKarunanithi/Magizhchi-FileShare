package com.magizhchi.share.model;

import com.google.gson.annotations.SerializedName;

/**
 * Mirrors the backend's ConnectionRequestResponse — surfaced in the
 * Notifications screen for both the "Received" pending inbox and (future)
 * the "Sent" outbox. createdAt comes back as an ISO-8601 string on the
 * Android side (Gson default) so we keep it as String here.
 */
public class ConnectionRequestResponse {

    @SerializedName("id")
    private String id;

    @SerializedName("senderId")
    private String senderId;

    @SerializedName("senderName")
    private String senderName;

    @SerializedName("senderPhotoUrl")
    private String senderPhotoUrl;

    @SerializedName("receiverId")
    private String receiverId;

    @SerializedName("receiverName")
    private String receiverName;

    @SerializedName("receiverPhotoUrl")
    private String receiverPhotoUrl;

    /** PENDING | ACCEPTED | REJECTED | CANCELLED */
    @SerializedName("status")
    private String status;

    @SerializedName("createdAt")
    private String createdAt;

    @SerializedName("respondedAt")
    private String respondedAt;

    public ConnectionRequestResponse() {}

    public String getId() { return id; }
    public String getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public String getSenderPhotoUrl() { return senderPhotoUrl; }
    public String getReceiverId() { return receiverId; }
    public String getReceiverName() { return receiverName; }
    public String getReceiverPhotoUrl() { return receiverPhotoUrl; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
    public String getRespondedAt() { return respondedAt; }
}
