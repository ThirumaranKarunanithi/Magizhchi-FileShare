package com.magizhchi.share.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class GroupMemberResponse {
    private Long    userId;
    private String  displayName;
    private String  profilePhotoUrl;
    private String  role;       // ADMIN | MEMBER
    private Instant joinedAt;
}
