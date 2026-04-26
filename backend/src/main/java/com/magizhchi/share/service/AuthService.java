package com.magizhchi.share.service;

import com.magizhchi.share.dto.request.LoginRequest;
import com.magizhchi.share.dto.request.RegisterRequest;
import com.magizhchi.share.dto.request.VerifyOtpRequest;
import com.magizhchi.share.dto.response.AuthResponse;
import com.magizhchi.share.exception.AppException;
import com.magizhchi.share.model.OtpCode;
import com.magizhchi.share.model.User;
import com.magizhchi.share.repository.UserRepository;
import com.magizhchi.share.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository   userRepo;
    private final OtpService       otpService;
    private final JwtTokenProvider jwtProvider;

    // ── OTP-based registration ────────────────────────────────────────────────

    /**
     * Step 1: client sends mobile number (or email).
     * We create the user as unverified, then send OTP.
     */
    @Transactional
    public void sendRegistrationOtp(RegisterRequest req) {
        String identifier = primaryIdentifier(req);

        // Allow re-send if user already exists but is unverified
        if (userRepo.existsByMobileNumber(identifier)) {
            User existing = userRepo.findByMobileNumber(identifier).orElseThrow();
            if (existing.getIsVerified()) {
                throw new AppException(HttpStatus.CONFLICT,
                        "An account with this mobile number already exists.");
            }
        } else if (req.getEmail() != null && userRepo.existsByEmail(req.getEmail())) {
            throw new AppException(HttpStatus.CONFLICT,
                    "An account with this email already exists.");
        } else {
            // Create placeholder user
            User user = User.builder()
                    .mobileNumber(req.getMobileNumber())
                    .email(req.getEmail())
                    .displayName(req.getDisplayName())
                    .isVerified(false)
                    .build();
            userRepo.save(user);
        }

        otpService.sendOtp(identifier, OtpCode.OtpPurpose.REGISTRATION);
    }

    /**
     * Step 2: verify OTP → mark user as verified → issue JWT.
     */
    @Transactional
    public AuthResponse verifyRegistrationOtp(VerifyOtpRequest req) {
        String identifier = req.getIdentifier();
        otpService.verifyOtp(identifier, req.getCode(), OtpCode.OtpPurpose.REGISTRATION);

        User user = userRepo.findByMobileOrEmail(identifier)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found."));

        user.setIsVerified(true);
        user.setLastSeenAt(Instant.now());
        userRepo.save(user);

        return buildAuthResponse(user);
    }

    // ── Login (existing users) ─────────────────────────────────────────────────

    /**
     * Passwordless login: send OTP to registered mobile/email.
     */
    @Transactional
    public void sendLoginOtp(LoginRequest req) {
        User user = userRepo.findByMobileOrEmail(req.getIdentifier())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "No account found with this mobile number or email."));

        if (!user.getIsVerified()) {
            throw new AppException(HttpStatus.FORBIDDEN,
                    "Account is not verified. Please complete registration first.");
        }

        otpService.sendOtp(req.getIdentifier(), OtpCode.OtpPurpose.LOGIN);
    }

    /**
     * Verify login OTP → issue JWT.
     */
    @Transactional
    public AuthResponse verifyLoginOtp(VerifyOtpRequest req) {
        otpService.verifyOtp(req.getIdentifier(), req.getCode(), OtpCode.OtpPurpose.LOGIN);

        User user = userRepo.findByMobileOrEmail(req.getIdentifier())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found."));

        user.setLastSeenAt(Instant.now());
        userRepo.save(user);

        return buildAuthResponse(user);
    }

    // ── Token refresh ─────────────────────────────────────────────────────────

    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtProvider.validate(refreshToken)) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token.");
        }
        Long userId = jwtProvider.getUserId(refreshToken);
        User user   = userRepo.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found."));
        return buildAuthResponse(user);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AuthResponse buildAuthResponse(User user) {
        return AuthResponse.builder()
                .accessToken(jwtProvider.generateAccessToken(user.getId()))
                .refreshToken(jwtProvider.generateRefreshToken(user.getId()))
                .userId(user.getId())
                .displayName(user.getDisplayName())
                .mobileNumber(user.getMobileNumber())
                .email(user.getEmail())
                .profilePhotoUrl(user.getProfilePhotoUrl())
                .build();
    }

    private String primaryIdentifier(RegisterRequest req) {
        return req.getMobileNumber() != null ? req.getMobileNumber() : req.getEmail();
    }
}
