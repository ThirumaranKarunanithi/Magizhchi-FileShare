package com.magizhchi.share.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SmsService {

    @Value("${twilio.account-sid}") private String accountSid;
    @Value("${twilio.auth-token}")  private String authToken;
    @Value("${twilio.from-number}") private String fromNumber;
    @Value("${otp.mock}")           private boolean mock;

    @PostConstruct
    public void init() {
        if (!mock && accountSid != null && !accountSid.isBlank()) {
            Twilio.init(accountSid, authToken);
            log.info("Twilio SMS service initialized");
        }
    }

    public void sendSms(String toNumber, String body) {
        if (mock) {
            log.warn("[SMS MOCK] To={} | Body={}", toNumber, body);
            return;
        }
        try {
            Message msg = Message.creator(
                    new PhoneNumber(toNumber),
                    new PhoneNumber(fromNumber),
                    body
            ).create();
            log.info("SMS sent: sid={}", msg.getSid());
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", toNumber, e.getMessage());
            throw new RuntimeException("SMS delivery failed: " + e.getMessage());
        }
    }
}
