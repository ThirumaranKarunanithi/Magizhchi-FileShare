package com.magizhchi.share.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    /** Either mobileNumber or email must be supplied */
    private String mobileNumber;
    private String email;

    @NotBlank
    @Size(min = 2, max = 80)
    private String displayName;
}
