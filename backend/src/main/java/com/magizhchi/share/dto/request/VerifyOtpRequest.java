package com.magizhchi.share.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyOtpRequest {
    @NotBlank private String identifier;
    @NotBlank private String code;
}
