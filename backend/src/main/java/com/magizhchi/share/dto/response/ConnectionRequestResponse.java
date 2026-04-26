package com.magizhchi.share.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data @Builder
public class ConnectionRequestResponse {
    private Long    id;
    private Long    senderId;
    private String  senderName;
    private String  senderPhotoUrl;
    private Long    receiverId;
    private String  receiverName;
    private String  receiverPhotoUrl;
    private String  status;          // PENDING | ACCEPTED | REJECTED | CANCELLED
    private Instant createdAt;
    private Instant respondedAt;
}
