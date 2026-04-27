package com.magizhchi.share.service;

import com.magizhchi.share.exception.AppException;
import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Wraps Twilio Verify v2 for OTP send + check.
 *
 * In mock mode (otp.mock=true) both methods are no-ops —
 * OtpService handles everything via the DB/Redis path.
 */
@Service
@Slf4j
public class SmsService {

    @Value("${twilio.account-sid}")        private String accountSid;
    @Value("${twilio.auth-token}")         private String authToken;
    @Value("${twilio.verify-service-sid}") private String verifyServiceSid;
    @Value("${otp.mock}")                  private boolean mock;

    @PostConstruct
    public void init() {
        if (!mock && accountSid != null && !accountSid.isBlank()) {
            Twilio.init(accountSid, authToken);
            log.info("Twilio Verify service initialized (sid={})", verifyServiceSid);
        }
    }

    /**
     * Ask Twilio Verify to send a 6-digit OTP via SMS to {@code toNumber}.
     * No-op in mock mode.
     */
    public void sendVerification(String toNumber) {
        if (mock) {
            log.warn("[VERIFY MOCK] Would send OTP to {}", toNumber);
            return;
        }
        try {
            Verification v = Verification.creator(verifyServiceSid, toNumber, "sms").create();
            log.info("Twilio Verify sent: to={}, status={}", toNumber, v.getStatus());
        } catch (ApiException e) {
            log.error("Twilio Verify send failed: to={}, code={}, msg={}",
                    toNumber, e.getCode(), e.getMessage());
            throw translateTwilioError(e, "send");
        }
    }

    // ── Twilio error mapping ──────────────────────────────────────────────────

    /**
     * Convert a Twilio {@link ApiException} into an {@link AppException} with the
     * correct HTTP status so the global handler returns a clean JSON error to the client.
     *
     * Known Twilio Verify error codes:
     *  20429 — Too many requests (account-level or service-level throttle)
     *  60203 — Max send attempts for this number reached within the window
     *  60200 — Invalid phone number / not a mobile number
     *  60205 — Landline / VoIP number not supported for SMS
     *  20404 — Verify service not found (misconfigured SID)
     */
    private AppException translateTwilioError(ApiException e, String operation) {
        int code = e.getCode() != null ? e.getCode() : -1;
        return switch (code) {
            case 20429, 60203 ->
                new AppException(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many OTP requests for this number. Please wait a minute before trying again.");
            case 60200, 60205 ->
                new AppException(HttpStatus.BAD_REQUEST,
                        "This phone number cannot receive SMS. Please use a valid mobile number.");
            case 20404 ->
                new AppException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "OTP service is misconfigured. Please contact support.");
            default ->
                new AppException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Could not " + operation + " OTP at this time. Please try again shortly.");
        };
    }

    /**
     * Ask Twilio Verify to check whether {@code code} is correct for {@code toNumber}.
     *
     * @return {@code true} if Twilio returns status "approved"
     */
    public boolean checkVerification(String toNumber, String code) {
        if (mock) {
            // In mock mode OtpService does the DB check — this method is never called.
            log.warn("[VERIFY MOCK] checkVerification called for {} — should not happen in mock mode", toNumber);
            return false;
        }
        try {
            VerificationCheck check = VerificationCheck.creator(verifyServiceSid)
                    .setTo(toNumber)
                    .setCode(code)
                    .create();
            log.info("Twilio Verify check: to={}, status={}", toNumber, check.getStatus());
            return "approved".equalsIgnoreCase(check.getStatus());
        } catch (ApiException e) {
            log.warn("Twilio Verify check failed: to={}, code={}, msg={}",
                    toNumber, e.getCode(), e.getMessage());
            int errCode = e.getCode() != null ? e.getCode() : -1;
            if (errCode == 20404 || errCode == 60200) {
                // 20404 = verification not found (expired / already used / bad number)
                // Return false so the service layer shows "Incorrect or expired OTP"
                return false;
            }
            // Any other Twilio error (rate limit on check, service down, etc.) — surface it
            throw translateTwilioError(e, "verify");
        }
    }
}
