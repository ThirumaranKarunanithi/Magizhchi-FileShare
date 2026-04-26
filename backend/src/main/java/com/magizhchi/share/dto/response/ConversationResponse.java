package com.magizhchi.share.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data @Builder
public class ConversationResponse {
    private Long                id;
    private String              type;         // DIRECT | GROUP | PERSONAL
    private String              name;
    private String              iconUrl;
    private int                 memberCount;
    private FileMessageResponse lastFile;
    private Instant             createdAt;
    /** For DIRECT conversations only — the other participant's user ID. Used for block action. */
    private Long                otherUserId;
}
