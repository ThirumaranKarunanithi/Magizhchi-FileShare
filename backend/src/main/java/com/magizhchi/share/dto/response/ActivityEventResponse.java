package com.magizhchi.share.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ActivityEventResponse {
    private Long    id;
    private String  eventType;
    private Long    actorId;
    private String  actorName;
    private String  actorPhotoUrl;
    private String  description;
    private Long    conversationId;
    private Long    fileMessageId;
    private Long    folderId;
    private Instant createdAt;
}
