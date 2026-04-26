package com.magizhchi.share.dto.response;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class UserSearchResponse {

    private Long   id;
    private String displayName;
    private String profilePhotoUrl;

    /**
     * Only populated when connectionStatus = CONNECTED or SELF.
     * Non-connections see null so personal contact info stays private.
     */
    private String mobileNumber;
    private String statusMessage;

    /**
     * Relationship from the searching user's perspective:
     *   SELF              – the caller's own profile
     *   CONNECTED         – accepted connection exists
     *   PENDING_SENT      – caller sent a request, awaiting response
     *   PENDING_RECEIVED  – the target sent a request to the caller
     *   BLOCKED_BY_ME     – caller has blocked this user
     *   NONE              – no relationship
     */
    private String connectionStatus;

    /** Present for PENDING_SENT / PENDING_RECEIVED – used to accept/reject/cancel. */
    private Long   connectionRequestId;
}
