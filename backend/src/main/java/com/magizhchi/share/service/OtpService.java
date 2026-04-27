package com.magizhchi.share.service;

import com.magizhchi.share.exception.AppException;
import com.magizhchi.share.model.OtpCode;
import com.magizhchi.share.repository.OtpCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * OTP orchestration service.
 *
 * Phone (E.164) identifiers:
 *   MOCK  — generate locally, save to DB, print in logs.
 *   PROD  — delegate send + check to Twilio Verify (stateless on our side).
 *
 * Email identifiers (login only — registration always requires a phone):
 *   MOCK  — generate locally, save to DB, print in logs.
 *   PROD  — generate locally, save to DB, send via SMTP (never touches Twilio).
 *
 * Rate limiting (Redis) is applied to every identifier regardless of mode.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final OtpCodeRepository   otpRepo;
    private final StringRedisTemplate redis;
    private final SmsService          smsService;
    private final EmailService        emailService;

    @Value("${otp.length}")                    private int     otpLength;
    @Value("${otp.expiry-minutes}")            private int     expiryMinutes;
    @Value("${otp.max-attempts}")              private int     maxAttempts;
    @Value("${otp.rate-limit-window-minutes}") private int     rateLimitWindowMinutes;
    @Value("${otp.rate-limit-max-sends}")      private int     rateLimitMaxSends;
    @Value("${otp.mock}")                      private boolean mock;

    private static final SecureRandom RANDOM = new SecureRandom();

    // ── Send ──────────────────────────────────────────────────────────────────

    /**
     * Send an OTP to {@code identifier} (E.164 phone or e-mail address).
     * Applies Redis rate limiting regardless of channel or mode.
     */
    @Transactional
    public void sendOtp(String identifier, OtpCode.OtpPurpose purpose) {

        // ── Per-send cooldown (atomic SET NX EX — race-condition-safe) ──────
        String cooldownKey = "otp:cooldown:" + identifier;
        Boolean acquired = redis.opsForValue()
                .setIfAbsent(cooldownKey, "1", 60, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(acquired)) {
            Long ttl  = redis.getExpire(cooldownKey, TimeUnit.SECONDS);
            long wait = (ttl != null && ttl > 0) ? ttl : 60;
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait " + wait + " second" + (wait == 1 ? "" : "s") +
                    " before requesting a new code.");
        }

        // ── Per-window rate limit (phone only — email has no SMS cost) ───────
        if (!isEmail(identifier)) {
            String rateLimitKey = "otp:rate:" + identifier;
            Long sends = redis.opsForValue().increment(rateLimitKey);
            if (sends != null && sends == 1) {
                redis.expire(rateLimitKey, rateLimitWindowMinutes, TimeUnit.MINUTES);
            }
            if (sends != null && sends > rateLimitMaxSends) {
                throw new AppException(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many OTP requests. Please wait before trying again.");
            }
        }

        // ── Route by identifier type ──────────────────────────────────────────
        if (isEmail(identifier)) {
            sendEmailOtp(identifier, purpose);
        } else {
            sendPhoneOtp(identifier, purpose);
        }
    }

    // ── Verify ────────────────────────────────────────────────────────────────

    /**
     * Verify the OTP code entered by the user.
     * Email OTPs always use the DB path.
     * Phone OTPs use Twilio in production, DB in mock mode.
     */
    @Transactional
    public void verifyOtp(String identifier, String code, OtpCode.OtpPurpose purpose) {

        if (mock || isEmail(identifier)) {
            // DB-based verification (mock SMS, or email OTP in any mode)
            verifyFromDb(identifier, code, purpose);
        } else {
            // Production phone: ask Twilio Verify
            boolean approved = smsService.checkVerification(identifier, code);
            if (!approved) {
                throw new AppException(HttpStatus.BAD_REQUEST,
                        "Incorrect or expired OTP. Please try again or request a new code.");
            }
        }

        // Clear rate-limit counter on successful verification
        redis.delete("otp:rate:" + identifier);
        redis.delete("otp:cooldown:" + identifier);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private boolean isEmail(String identifier) {
        return identifier != null && identifier.contains("@");
    }

    /** Phone OTP: Twilio in prod, DB-logged in mock. */
    private void sendPhoneOtp(String identifier, OtpCode.OtpPurpose purpose) {
        if (mock) {
            saveToDb(identifier, purpose, generateCode(), true);
        } else {
            smsService.sendVerification(identifier);
        }
    }

    /**
     * Email OTP: always generate + save to DB.
     * In mock mode, log the code; in production, send via SMTP.
     */
    private void sendEmailOtp(String identifier, OtpCode.OtpPurpose purpose) {
        String code = generateCode();
        saveToDb(identifier, purpose, code, mock); // log only in mock

        if (!mock) {
            emailService.sendOtp(identifier, code, expiryMinutes);
        }
    }

    /** Persist OTP to DB; log the code when {@code logCode} is true (mock). */
    private void saveToDb(String identifier, OtpCode.OtpPurpose purpose,
                          String code, boolean logCode) {
        Instant now = Instant.now();
        otpRepo.save(OtpCode.builder()
                .identifier(identifier)
                .code(code)
                .purpose(purpose)
                .expiresAt(now.plus(expiryMinutes, ChronoUnit.MINUTES))
                .createdAt(now)
                .build());
        if (logCode) {
            log.warn("🔑 [OTP MOCK] {} → code={}", identifier, code);
        }
    }

    /** Validate an OTP stored in DB (email or mock-phone). */
    private void verifyFromDb(String identifier, String code, OtpCode.OtpPurpose purpose) {
        OtpCode otp = otpRepo
                .findTopByIdentifierAndPurposeAndIsUsedFalseOrderByCreatedAtDesc(identifier, purpose)
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST,
                        "No pending OTP found. Please request a new code."));

        if (otp.isExpired()) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "OTP has expired. Please request a new one.");
        }
        if (otp.getAttempts() >= maxAttempts) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Maximum attempts exceeded. Please request a new OTP.");
        }

        otp.setAttempts(otp.getAttempts() + 1);

        if (!otp.isValid(code)) {
            otpRepo.save(otp);
            int remaining = maxAttempts - otp.getAttempts();
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Incorrect OTP. " + remaining + " attempt(s) remaining.");
        }

        otp.setIsUsed(true);
        otpRepo.save(otp);
    }

    private String generateCode() {
        int max = (int) Math.pow(10, otpLength);
        return String.format("%0" + otpLength + "d", RANDOM.nextInt(max));
    }

    /** Nightly cleanup of expired OTP records. */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredOtps() {
        int deleted = otpRepo.deleteExpired(Instant.now());
        log.info("Cleaned up {} expired OTP records", deleted);
    }
}
