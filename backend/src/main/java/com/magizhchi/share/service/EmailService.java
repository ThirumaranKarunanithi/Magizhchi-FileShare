package com.magizhchi.share.service;

import com.magizhchi.share.exception.AppException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails (OTP codes).
 * Requires MAIL_USERNAME + MAIL_PASSWORD environment variables.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    /**
     * Send a 6-digit OTP to the given email address.
     */
    public void sendOtp(String toEmail, String code, int expiryMinutes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, "Magizhchi Share");
            helper.setTo(toEmail);
            helper.setSubject("Your Magizhchi Share sign-in code: " + code);
            helper.setText(buildHtml(code, expiryMinutes), true);
            mailSender.send(message);
            log.info("OTP email sent: to={}", toEmail);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not send OTP email. Please check your email address or try again shortly.");
        }
    }

    // ── Email template ────────────────────────────────────────────────────────

    private String buildHtml(String code, int expiryMinutes) {
        return """
            <!DOCTYPE html>
            <html>
            <body style="margin:0;padding:0;background:#f0f9ff;font-family:Arial,sans-serif;">
              <div style="max-width:480px;margin:40px auto;padding:24px;">

                <!-- Header -->
                <div style="text-align:center;margin-bottom:24px;">
                  <h1 style="color:#0369a1;font-size:22px;margin:0;">Magizhchi Share</h1>
                  <p style="color:#64748b;font-size:14px;margin:6px 0 0;">Secure File Sharing</p>
                </div>

                <!-- Card -->
                <div style="background:#ffffff;border-radius:16px;padding:36px 32px;
                             box-shadow:0 4px 24px rgba(0,0,0,0.08);text-align:center;">

                  <!-- Icon -->
                  <div style="width:56px;height:56px;background:#e0f2fe;border-radius:50%;
                               display:inline-flex;align-items:center;justify-content:center;
                               margin-bottom:20px;font-size:26px;">
                    🔐
                  </div>

                  <h2 style="color:#1e293b;font-size:18px;margin:0 0 8px;">Sign-in Verification</h2>
                  <p style="color:#64748b;font-size:14px;margin:0 0 28px;">
                    Use the code below to complete your sign-in.
                  </p>

                  <!-- OTP code box -->
                  <div style="display:inline-block;background:#f0f9ff;border:2px dashed #7dd3fc;
                               border-radius:12px;padding:18px 36px;margin-bottom:24px;">
                    <span style="font-size:36px;font-weight:700;letter-spacing:10px;color:#0284c7;">
                      %s
                    </span>
                  </div>

                  <p style="color:#94a3b8;font-size:13px;margin:0;">
                    This code expires in <strong>%d minutes</strong>.
                    Do not share it with anyone.
                  </p>
                </div>

                <!-- Footer -->
                <p style="color:#cbd5e1;font-size:11px;text-align:center;margin-top:20px;">
                  If you didn't request this code, you can safely ignore this email.
                </p>
              </div>
            </body>
            </html>
            """.formatted(code, expiryMinutes);
    }
}
