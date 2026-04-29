package com.magizhchi.share.service;

import com.magizhchi.share.dto.request.LoginRequest;
import com.magizhchi.share.dto.request.RegisterRequest;
import com.magizhchi.share.dto.request.VerifyOtpRequest;
import com.magizhchi.share.dto.response.AuthResponse;
import com.magizhchi.share.exception.AppException;
import com.magizhchi.share.model.OtpCode;
import com.magizhchi.share.model.PendingRegistration;
import com.magizhchi.share.model.User;
import com.magizhchi.share.repository.PendingRegistrationRepository;
import com.magizhchi.share.repository.UserRepository;
import com.magizhchi.share.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository                userRepo;
    private final PendingRegistrationRepository pendingRepo;
    private final OtpService                    otpService;
    private final JwtTokenProvider              jwtProvider;
    private final FileStorageService            fileStorage;

    /**
     * How long a pending registration (form filled, OTP not yet verified) is
     * kept around before being auto-swept. Slightly longer than the OTP TTL
     * so users can request a fresh OTP without re-typing their details.
     */
    private static final int PENDING_REGISTRATION_TTL_MINUTES = 30;

    // ── OTP-based registration ────────────────────────────────────────────────

    /**
     * Step 1: client sends mobile number, email, and display name.
     *
     * <p>Unlike the previous implementation, this does <strong>not</strong>
     * insert a {@link User} row. Each unverified User row used to burn a
     * value out of the {@code users.id} IDENTITY sequence, leaving permanent
     * gaps in the primary key when the user abandoned the flow or hit a
     * unique-constraint failure. We now stage the registration in a
     * dedicated {@link PendingRegistration} table; the User row is only
     * created in {@link #verifyRegistrationOtp(VerifyOtpRequest)} once the
     * OTP is confirmed, so the User PK sequence stays dense.
     */
    @Transactional
    public void sendRegistrationOtp(RegisterRequest req) {
        String mobile = req.getMobileNumber().trim();
        String email  = req.getEmail().trim();

        // Block if either field is already taken by a VERIFIED account.
        // (Legacy unverified User rows from the old flow are NOT a conflict
        // here — they'll be ignored at verify-time and can be cleaned up
        // separately if desired.)
        userRepo.findByMobileNumber(mobile)
                .filter(User::getIsVerified)
                .ifPresent(u -> { throw new AppException(HttpStatus.CONFLICT,
                        "An account with this mobile number already exists."); });
        userRepo.findByEmail(email)
                .filter(User::getIsVerified)
                .ifPresent(u -> { throw new AppException(HttpStatus.CONFLICT,
                        "An account with this email already exists."); });

        // Upsert the pending registration row keyed by mobile, falling back
        // to email. Re-submitting the form replaces the previous pending
        // entry rather than stacking duplicates.
        PendingRegistration pending = pendingRepo.findByMobileNumber(mobile)
                .or(() -> pendingRepo.findByEmail(email))
                .orElseGet(PendingRegistration::new);

        Instant now = Instant.now();
        pending.setMobileNumber(mobile);
        pending.setEmail(email);
        pending.setDisplayName(req.getDisplayName());
        if (pending.getCreatedAt() == null) pending.setCreatedAt(now);
        pending.setExpiresAt(now.plus(PENDING_REGISTRATION_TTL_MINUTES, ChronoUnit.MINUTES));
        pendingRepo.save(pending);

        // Send OTP to the user's preferred channel (email by default)
        String identifier = "SMS".equalsIgnoreCase(req.getOtpChannel()) ? mobile : email;
        otpService.sendOtp(identifier, OtpCode.OtpPurpose.REGISTRATION);
    }

    /**
     * Step 2: verify OTP → create the User row → issue JWT.
     *
     * <p>This is the FIRST point at which a User row exists. By deferring
     * the insert until OTP success, the {@code users.id} IDENTITY sequence
     * only advances for accounts that actually complete registration, so
     * the primary key stays dense.
     */
    @Transactional
    public AuthResponse verifyRegistrationOtp(VerifyOtpRequest req) {
        String identifier = req.getIdentifier();
        otpService.verifyOtp(identifier, req.getCode(), OtpCode.OtpPurpose.REGISTRATION);

        // First check for legacy: an already-verified user (idempotent path —
        // for instance, the client retries verify after a timeout but the
        // server already created the row on the previous call).
        java.util.Optional<User> existing = userRepo.findByMobileOrEmail(identifier);
        if (existing.isPresent() && Boolean.TRUE.equals(existing.get().getIsVerified())) {
            User u = existing.get();
            u.setLastSeenAt(Instant.now());
            return buildAuthResponse(userRepo.save(u));
        }

        // Locate the staged details — created by sendRegistrationOtp().
        PendingRegistration pending = pendingRepo.findByMobileOrEmail(identifier)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Registration session not found. Please start over."));

        // Defensive race-check — between sendRegistrationOtp and now another
        // user could have completed registration with the same mobile/email.
        userRepo.findByMobileNumber(pending.getMobileNumber())
                .filter(User::getIsVerified)
                .ifPresent(u -> { throw new AppException(HttpStatus.CONFLICT,
                        "An account with this mobile number already exists."); });
        userRepo.findByEmail(pending.getEmail())
                .filter(User::getIsVerified)
                .ifPresent(u -> { throw new AppException(HttpStatus.CONFLICT,
                        "An account with this email already exists."); });

        // If a stale unverified User row exists from the legacy flow, reuse
        // it (don't burn a fresh id). Otherwise create a brand-new row.
        User user = existing.orElseGet(() -> User.builder()
                .mobileNumber(pending.getMobileNumber())
                .email(pending.getEmail())
                .displayName(pending.getDisplayName())
                .build());

        user.setMobileNumber(pending.getMobileNumber());
        user.setEmail(pending.getEmail());
        user.setDisplayName(pending.getDisplayName());
        user.setIsVerified(true);
        user.setLastSeenAt(Instant.now());
        User saved = userRepo.save(user);

        // Pending row done its job — drop it.
        pendingRepo.delete(pending);

        return buildAuthResponse(saved);
    }

    /**
     * Nightly sweeper for pending registrations that were never verified.
     * Without this, the table would grow indefinitely as users abandon the
     * flow after the OTP step. Mirrors {@link OtpService#cleanupExpiredOtps()}.
     */
    @Scheduled(cron = "0 5 3 * * *")
    @Transactional
    public void cleanupExpiredPendingRegistrations() {
        int deleted = pendingRepo.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired pending-registration records", deleted);
        }
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
        // Re-presign the profile photo URL on every auth response — the
        // value stored in the DB is itself a presigned link with a TTL,
        // so just-logged-in clients would otherwise get a URL that may
        // already have expired since the last upload.
        String freshPhoto = fileStorage.refreshProfilePhotoUrl(user.getProfilePhotoUrl());
        return AuthResponse.builder()
                .accessToken(jwtProvider.generateAccessToken(user.getId()))
                .refreshToken(jwtProvider.generateRefreshToken(user.getId()))
                .userId(user.getId())
                .displayName(user.getDisplayName())
                .mobileNumber(user.getMobileNumber())
                .email(user.getEmail())
                .profilePhotoUrl(freshPhoto != null ? freshPhoto : user.getProfilePhotoUrl())
                .build();
    }

    // primaryIdentifier() removed — channel-aware selection is now inline in sendRegistrationOtp()
}
