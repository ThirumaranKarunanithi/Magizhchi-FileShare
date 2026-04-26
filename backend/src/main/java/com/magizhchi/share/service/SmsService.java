package com.magizhchi.share.service;

import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
            throw new RuntimeException("Could not send OTP: " + e.getMessage(), e);
        }
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
            // Twilio throws 404 when the verification does not exist (expired / already used)
            log.warn("Twilio Verify check failed: to={}, code={}, msg={}",
                    toNumber, e.getCode(), e.getMessage());
            return false;
        }
    }
}
