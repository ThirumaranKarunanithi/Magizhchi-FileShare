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
 * In MOCK mode  (otp.mock=true, default in dev):
 *   – Generates a code locally, saves to DB, prints it in the log.
 *   – Verification reads the code back from DB (no Twilio calls).
 *
 * In PRODUCTION mode (otp.mock=false):
 *   – Delegates to Twilio Verify for both send and check.
 *   – No OTP codes are stored locally; Twilio manages the state.
 *   – Rate limiting (Redis) is still applied on our side.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final OtpCodeRepository   otpRepo;
    private final StringRedisTemplate redis;
    private final SmsService          smsService;

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
     * Applies rate limiting in Redis regardless of mode.
     */
    @Transactional
    public void sendOtp(String identifier, OtpCode.OtpPurpose purpose) {

        // Rate-limit: max N sends per window (both mock and real)
        String rateLimitKey = "otp:rate:" + identifier;
        Long sends = redis.opsForValue().increment(rateLimitKey);
        if (sends != null && sends == 1) {
            redis.expire(rateLimitKey, rateLimitWindowMinutes, TimeUnit.MINUTES);
        }
        if (sends != null && sends > rateLimitMaxSends) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many OTP requests. Please wait before trying again.");
        }

        if (mock) {
            // ── Mock: generate locally, save to DB, log ──
            String code = generateCode();
            Instant now = Instant.now();
            otpRepo.save(OtpCode.builder()
                    .identifier(identifier)
                    .code(code)
                    .purpose(purpose)
                    .expiresAt(now.plus(expiryMinutes, ChronoUnit.MINUTES))
                    .createdAt(now)
                    .build());
            log.warn("🔑 [OTP MOCK] {} → code={}", identifier, code);

        } else {
            // ── Production: delegate to Twilio Verify ──
            boolean isPhone = identifier.startsWith("+") || identifier.matches("\\d{10,15}");
            if (isPhone) {
                smsService.sendVerification(identifier);
            } else {
                // e-mail OTP — Twilio Verify supports email too but requires extra setup;
                // log for now and extend later.
                log.info("EMAIL OTP requested for {} — not yet wired to Verify", identifier);
                throw new AppException(HttpStatus.NOT_IMPLEMENTED,
                        "Email OTP is not supported yet. Please use your mobile number.");
            }
        }
    }

    // ── Verify ────────────────────────────────────────────────────────────────

    /**
     * Verify the OTP entered by the user.
     * Throws {@link AppException} on failure; returns normally on success.
     */
    @Transactional
    public void verifyOtp(String identifier, String code, OtpCode.OtpPurpose purpose) {

        if (mock) {
            // ── Mock: check DB record ──
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

        } else {
            // ── Production: ask Twilio Verify ──
            boolean approved = smsService.checkVerification(identifier, code);
            if (!approved) {
                throw new AppException(HttpStatus.BAD_REQUEST,
                        "Incorrect or expired OTP. Please try again or request a new code.");
            }
        }

        // Clear rate-limit counter on successful verification
        redis.delete("otp:rate:" + identifier);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String generateCode() {
        int max = (int) Math.pow(10, otpLength);
        return String.format("%0" + otpLength + "d", RANDOM.nextInt(max));
    }

    /** Nightly cleanup of expired mock OTP records (no-op in production but harmless). */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredOtps() {
        int deleted = otpRepo.deleteExpired(Instant.now());
        log.info("Cleaned up {} expired OTP records", deleted);
    }
}
