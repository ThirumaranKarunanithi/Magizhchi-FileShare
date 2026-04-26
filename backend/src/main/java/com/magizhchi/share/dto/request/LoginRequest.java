package com.magizhchi.share.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    /** Mobile number OR email */
    @NotBlank
    private String identifier;
}
