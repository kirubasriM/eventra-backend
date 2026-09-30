package com.eventra.backend.service.impl;

import com.eventra.backend.service.SmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

@Service
public class SmsServiceImpl implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsServiceImpl.class);

    private final boolean smsEnabled;
    private final String provider;
    private final String apiKey;
    private final String senderId;
    private final String templateId;
    private final String providerUrl;

    private final HttpClient httpClient;

    public SmsServiceImpl(
            @Value("${sms.enabled:false}") boolean smsEnabled,
            @Value("${sms.provider:msg91}") String provider,
            @Value("${sms.api-key:}") String apiKey,
            @Value("${sms.sender-id:}") String senderId,
            @Value("${sms.template-id:}") String templateId,
            @Value("${sms.provider-url:https://control.msg91.com/api/v5/flow/}") String providerUrl) {

        this.smsEnabled = smsEnabled;
        this.provider = (provider == null || provider.isBlank()) ? "msg91" : provider.trim().toLowerCase();
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.senderId = senderId != null ? senderId.trim() : "";
        this.templateId = templateId != null ? templateId.trim() : "";
        this.providerUrl = providerUrl != null ? providerUrl.trim() : "https://control.msg91.com/api/v5/flow/";

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        if (smsEnabled) {
            log.info("Real SMS OTP service initialized with provider: {}", this.provider);
        } else {
            log.info("SMS OTP service is running in DEVELOPMENT/DEMO mode (OTP_SMS_ENABLED=false)");
        }
    }

    @Override
    public boolean isSmsEnabled() {
        return smsEnabled;
    }

    @Override
    public boolean sendOtp(String phone, String otp, int expiryMinutes) {
        if (!smsEnabled) {
            log.info("[DEV MODE] Real SMS dispatch skipped (OTP_SMS_ENABLED=false) for +91 {}", maskPhone(phone));
            return true;
        }

        if (apiKey.isBlank()) {
            log.error("[SMS CONFIG ERROR] SMS_API_KEY is not configured. Cannot dispatch real SMS to +91 {}", maskPhone(phone));
            return false;
        }

        try {
            HttpRequest request = buildHttpRequest(phone, otp, expiryMinutes);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            if (statusCode >= 200 && statusCode < 300) {
                log.info("[SMS SUCCESS] OTP dispatched to +91 {} via provider {} (status: {})",
                        maskPhone(phone), provider, statusCode);
                return true;
            } else {
                log.error("[SMS FAILED] Provider {} returned HTTP status {} for recipient +91 {}",
                        provider, statusCode, maskPhone(phone));
                return false;
            }
        } catch (HttpTimeoutException e) {
            log.error("[SMS TIMEOUT] Provider {} timed out while sending SMS to +91 {}", provider, maskPhone(phone));
            return false;
        } catch (IOException | InterruptedException e) {
            log.error("[SMS ERROR] Failed to dispatch SMS via {}: {}", provider, e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.error("[SMS ERROR] Unexpected error dispatching SMS: {}", e.getMessage());
            return false;
        }
    }

    private HttpRequest buildHttpRequest(String phone, String otp, int expiryMinutes) {
        String jsonPayload;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(providerUrl))
                .timeout(Duration.ofSeconds(10));

        switch (provider) {
            case "fast2sms":
                builder.header("authorization", apiKey)
                        .header("Content-Type", "application/json");
                jsonPayload = String.format("{\"route\":\"otp\",\"variables_values\":\"%s\",\"numbers\":\"%s\"}",
                        escapeJson(otp), escapeJson(phone));
                break;

            case "generic":
                builder.header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json");
                jsonPayload = String.format("{\"mobile\":\"%s\",\"otp\":\"%s\",\"expiryMinutes\":%d}",
                        escapeJson(phone), escapeJson(otp), expiryMinutes);
                break;

            case "msg91":
            default:
                builder.header("accept", "application/json")
                        .header("authkey", apiKey)
                        .header("content-type", "application/json");
                jsonPayload = String.format(
                        "{\"template_id\":\"%s\",\"short_url\":\"0\",\"recipients\":[{\"mobiles\":\"91%s\",\"VAR1\":\"%s\",\"VAR2\":\"%d\"}]}",
                        escapeJson(templateId), escapeJson(phone), escapeJson(otp), expiryMinutes);
                break;
        }

        return builder.POST(HttpRequest.BodyPublishers.ofString(jsonPayload)).build();
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return phone.substring(0, 2) + "******" + phone.substring(phone.length() - 2);
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
