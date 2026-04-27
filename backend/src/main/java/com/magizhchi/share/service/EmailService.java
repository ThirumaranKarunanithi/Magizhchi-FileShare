package com.magizhchi.share.service;

import com.magizhchi.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Sends transactional emails (OTP codes) via Resend's HTTP API.
 * Uses HTTPS (port 443) — works on Railway where SMTP (ports 25/465/587) is blocked.
 *
 * Required env vars:
 *   RESEND_API_KEY   — from resend.com dashboard
 *   RESEND_FROM      — verified sender address, e.g. noreply@yourdomain.com
 */
@Service
@Slf4j
public class EmailService {

    private static final String RESEND_URL = "https://api.resend.com/emails";

    @Value("${resend.api-key:}")
    private String apiKey;

    @Value("${resend.from-address:}")
    private String fromAddress;

    private final RestTemplate restTemplate;

    public EmailService() {
        // 5-second connect + read timeouts so we never hang indefinitely
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(5_000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Send a 6-digit OTP to the given email address via Resend.
     */
    public void sendOtp(String toEmail, String code, int expiryMinutes) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("Email OTP requested but RESEND_API_KEY is not configured");
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Email login is not configured on this server. Please use your mobile number.");
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            log.error("Email OTP requested but RESEND_FROM is not configured");
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Email login is not configured on this server. Please use your mobile number.");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = Map.of(
                    "from",    fromAddress,
                    "to",      new String[]{ toEmail },
                    "subject", "Your Magizhchi Share sign-in code: " + code,
                    "html",    buildHtml(code, expiryMinutes)
            );

            ResponseEntity<Map> response = restTemplate.exchange(
                    RESEND_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Resend API returned {}: {}", response.getStatusCode(), response.getBody());
                throw new AppException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Could not send OTP email. Please try again shortly.");
            }

            log.info("OTP email sent via Resend: to={}", toEmail);

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to send OTP email to {} via Resend: {}", toEmail, e.getMessage());
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
                  <div style="width:56px;height:56px;background:#e0f2fe;border-radius:50%%;
                               display:inline-flex;align-items:center;justify-content:center;
                               margin-bottom:20px;font-size:26px;">
                    &#128272;
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
