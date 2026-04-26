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

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final OtpCodeRepository    otpRepo;
    private final StringRedisTemplate  redis;
    private final SmsService           smsService;

    @Value("${otp.length}")                    private int    otpLength;
    @Value("${otp.expiry-minutes}")            private int    expiryMinutes;
    @Value("${otp.max-attempts}")              private int    maxAttempts;
    @Value("${otp.rate-limit-window-minutes}") private int    rateLimitWindowMinutes;
    @Value("${otp.rate-limit-max-sends}")      private int    rateLimitMaxSends;
    @Value("${otp.mock}")                      private boolean mock;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generate + send an OTP. Applies rate limiting (Redis).
     * @param identifier Mobile number or email
     * @param purpose    Why we are sending this OTP
     */
    @Transactional
    public void sendOtp(String identifier, OtpCode.OtpPurpose purpose) {
        // Rate-limit check via Redis counter
        String rateLimitKey = "otp:rate:" + identifier;
        Long sends = redis.opsForValue().increment(rateLimitKey);
        if (sends != null && sends == 1) {
            redis.expire(rateLimitKey, rateLimitWindowMinutes, TimeUnit.MINUTES);
        }
        if (sends != null && sends > rateLimitMaxSends) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many OTP requests. Please wait before trying again.");
        }

        String code = generateCode();
        Instant now = Instant.now();

        OtpCode otp = OtpCode.builder()
                .identifier(identifier)
                .code(code)
                .purpose(purpose)
                .expiresAt(now.plus(expiryMinutes, ChronoUnit.MINUTES))
                .createdAt(now)
                .build();
        otpRepo.save(otp);

        if (mock) {
            log.warn("🔑 [OTP MOCK] {} → code={}", identifier, code);
        } else {
            // Route to SMS or email based on identifier format
            if (identifier.startsWith("+") || identifier.matches("\\d{10,15}")) {
                smsService.sendSms(identifier,
                        "Your Magizhchi Share verification code is: " + code +
                        "\nExpires in " + expiryMinutes + " minutes.");
            } else {
                // email — delegate to email service (or log for now)
                log.info("EMAIL OTP for {} = {}", identifier, code);
            }
        }
    }

    /**
     * Verify an OTP. Throws if invalid/expired/exceeded attempts.
     * Marks the OTP as used on success.
     */
    @Transactional
    public void verifyOtp(String identifier, String code, OtpCode.OtpPurpose purpose) {
        OtpCode otp = otpRepo
                .findTopByIdentifierAndPurposeAndIsUsedFalseOrderByCreatedAtDesc(identifier, purpose)
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST,
                        "No pending OTP found for this identifier."));

        if (otp.isExpired()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "OTP has expired. Please request a new one.");
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

        // Clear rate-limit counter on success
        redis.delete("otp:rate:" + identifier);
    }

    private String generateCode() {
        int max = (int) Math.pow(10, otpLength);
        return String.format("%0" + otpLength + "d", RANDOM.nextInt(max));
    }

    /** Nightly cleanup of expired OTPs */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredOtps() {
        int deleted = otpRepo.deleteExpired(Instant.now());
        log.info("Cleaned up {} expired OTP records", deleted);
    }
}
