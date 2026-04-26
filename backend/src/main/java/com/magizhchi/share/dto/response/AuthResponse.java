package com.magizhchi.share.dto.response;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private Long   userId;
    private String displayName;
    private String mobileNumber;
    private String email;
    private String profilePhotoUrl;
}
