package com.magizhchi.share.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Mobile number is required.")
    @Pattern(regexp = "^\\+?[1-9]\\d{6,14}$", message = "Enter a valid mobile number in E.164 format (e.g. +919876543210).")
    private String mobileNumber;

    @NotBlank(message = "Email address is required.")
    @Email(message = "Enter a valid email address.")
    private String email;

    @NotBlank(message = "Full name is required.")
    @Size(min = 2, max = 80)
    private String displayName;

    /**
     * Preferred OTP delivery channel: "EMAIL" (default) or "SMS".
     * The OTP is sent to the email address when EMAIL is chosen,
     * or to the mobile number when SMS is chosen.
     */
    private String otpChannel = "EMAIL";
}
