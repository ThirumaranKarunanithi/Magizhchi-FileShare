package com.magizhchi.share.controller;

import com.magizhchi.share.dto.request.LoginRequest;
import com.magizhchi.share.dto.request.RegisterRequest;
import com.magizhchi.share.dto.request.VerifyOtpRequest;
import com.magizhchi.share.dto.response.AuthResponse;
import com.magizhchi.share.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * POST /api/auth/register/send-otp   → send OTP for new account
 * POST /api/auth/register/verify     → verify OTP, create account, get JWT
 * POST /api/auth/login/send-otp      → send OTP for existing account
 * POST /api/auth/login/verify        → verify OTP, get JWT
 * POST /api/auth/refresh             → exchange refresh token for new access token
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // ── Registration ─────────────────────────────────────────────────────────

    @PostMapping("/register/send-otp")
    public ResponseEntity<Map<String, String>> registerSendOtp(
            @Valid @RequestBody RegisterRequest req) {
        authService.sendRegistrationOtp(req);
        return ResponseEntity.ok(Map.of("message",
                "Verification code sent. Please check your mobile number or email."));
    }

    @PostMapping("/register/verify")
    public ResponseEntity<AuthResponse> registerVerify(
            @Valid @RequestBody VerifyOtpRequest req) {
        return ResponseEntity.ok(authService.verifyRegistrationOtp(req));
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @PostMapping("/login/send-otp")
    public ResponseEntity<Map<String, String>> loginSendOtp(
            @Valid @RequestBody LoginRequest req) {
        authService.sendLoginOtp(req);
        return ResponseEntity.ok(Map.of("message",
                "Verification code sent."));
    }

    @PostMapping("/login/verify")
    public ResponseEntity<AuthResponse> loginVerify(
            @Valid @RequestBody VerifyOtpRequest req) {
        return ResponseEntity.ok(authService.verifyLoginOtp(req));
    }

    // ── Token refresh ─────────────────────────────────────────────────────────

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.refreshToken(body.get("refreshToken")));
    }
}
