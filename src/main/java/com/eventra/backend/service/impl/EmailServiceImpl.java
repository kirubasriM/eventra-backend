package com.eventra.backend.service.impl;

import com.eventra.backend.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final boolean emailEnabled;
    private final String provider;
    private final String username;
    private final String password;
    private final String host;
    private final int port;
    private final String fromEmail;
    private final String apiUrl;

    private final HttpClient httpClient;

    public EmailServiceImpl(
            @Value("${email.enabled:false}") boolean emailEnabled,
            @Value("${email.provider:smtp}") String provider,
            @Value("${email.username:}") String username,
            @Value("${email.password:}") String password,
            @Value("${email.host:smtp.gmail.com}") String host,
            @Value("${email.port:587}") int port,
            @Value("${email.from:no-reply@eventra.in}") String fromEmail,
            @Value("${email.api-url:}") String apiUrl) {

        this.emailEnabled = emailEnabled;
        this.provider = (provider == null || provider.isBlank()) ? "smtp" : provider.trim().toLowerCase();
        this.username = username != null ? username.trim() : "";
        this.password = password != null ? password.trim() : "";
        this.host = (host == null || host.isBlank()) ? "smtp.gmail.com" : host.trim();
        this.port = port > 0 ? port : 587;
        this.fromEmail = (fromEmail == null || fromEmail.isBlank()) ? "no-reply@eventra.in" : fromEmail.trim();
        this.apiUrl = apiUrl != null ? apiUrl.trim() : "";

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();

        if (emailEnabled) {
            log.info("Real Email OTP service initialized with provider: {}", this.provider);
        } else {
            log.info("Email OTP service is running in DEVELOPMENT/DEMO mode (EMAIL_ENABLED=false)");
        }
    }

    @Override
    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    @Override
    public boolean sendOtp(String email, String otp, int expiryMinutes) {
        if (!emailEnabled) {
            log.info("[DEV MODE] Real email dispatch skipped (EMAIL_ENABLED=false) for recipient: {}", maskEmail(email));
            return true;
        }

        try {
            switch (provider) {
                case "resend":
                    return sendViaResend(email, otp, expiryMinutes);
                case "sendgrid":
                    return sendViaSendGrid(email, otp, expiryMinutes);
                case "brevo":
                    return sendViaBrevo(email, otp, expiryMinutes);
                case "generic":
                case "http":
                    return sendViaGenericHttp(email, otp, expiryMinutes);
                case "smtp":
                default:
                    return sendViaSmtp(email, otp, expiryMinutes);
            }
        } catch (Exception e) {
            log.error("[EMAIL ERROR] Unexpected error dispatching email to {}: {}", maskEmail(email), e.getMessage());
            return false;
        }
    }

    private boolean sendViaResend(String email, String otp, int expiryMinutes) {
        String endpoint = apiUrl.isBlank() ? "https://api.resend.com/emails" : apiUrl;
        String json = String.format(
                "{\"from\":\"%s\",\"to\":[\"%s\"],\"subject\":\"Your Eventra Verification Code\",\"html\":\"%s\"}",
                escapeJson(fromEmail),
                escapeJson(email),
                escapeJson(buildHtmlBody(otp, expiryMinutes))
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + password)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return executeHttpRequest(request, "Resend", email);
    }

    private boolean sendViaSendGrid(String email, String otp, int expiryMinutes) {
        String endpoint = apiUrl.isBlank() ? "https://api.sendgrid.com/v3/mail/send" : apiUrl;
        String json = String.format(
                "{\"personalizations\":[{\"to\":[{\"email\":\"%s\"}]}],\"from\":{\"email\":\"%s\"},\"subject\":\"Your Eventra Verification Code\",\"content\":[{\"type\":\"text/html\",\"value\":\"%s\"}]}",
                escapeJson(email),
                escapeJson(fromEmail),
                escapeJson(buildHtmlBody(otp, expiryMinutes))
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + password)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return executeHttpRequest(request, "SendGrid", email);
    }

    private boolean sendViaBrevo(String email, String otp, int expiryMinutes) {
        String endpoint = apiUrl.isBlank() ? "https://api.brevo.com/v3/smtp/email" : apiUrl;
        String json = String.format(
                "{\"sender\":{\"email\":\"%s\"},\"to\":[{\"email\":\"%s\"}],\"subject\":\"Your Eventra Verification Code\",\"htmlContent\":\"%s\"}",
                escapeJson(fromEmail),
                escapeJson(email),
                escapeJson(buildHtmlBody(otp, expiryMinutes))
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("api-key", password)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return executeHttpRequest(request, "Brevo", email);
    }

    private boolean sendViaGenericHttp(String email, String otp, int expiryMinutes) {
        if (apiUrl.isBlank()) {
            log.error("[EMAIL CONFIG ERROR] EMAIL_API_URL is required for generic email provider");
            return false;
        }

        String json = String.format(
                "{\"to\":\"%s\",\"from\":\"%s\",\"otp\":\"%s\",\"expiryMinutes\":%d}",
                escapeJson(email),
                escapeJson(fromEmail),
                escapeJson(otp),
                expiryMinutes
        );

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10));

        if (!password.isBlank()) {
            builder.header("Authorization", "Bearer " + password);
        }

        HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(json)).build();
        return executeHttpRequest(request, "Generic HTTP", email);
    }

    private boolean executeHttpRequest(HttpRequest request, String providerName, String email) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code >= 200 && code < 300) {
                log.info("[EMAIL SUCCESS] OTP email dispatched to {} via {}", maskEmail(email), providerName);
                return true;
            } else {
                log.error("[EMAIL FAILED] {} API returned HTTP status {} for recipient {}", providerName, code, maskEmail(email));
                return false;
            }
        } catch (HttpTimeoutException e) {
            log.error("[EMAIL TIMEOUT] {} API timed out while sending to {}", providerName, maskEmail(email));
            return false;
        } catch (IOException | InterruptedException e) {
            log.error("[EMAIL ERROR] Failed to dispatch email via {}: {}", providerName, e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Sends email via standard SMTP protocol with authentication and SSL/STARTTLS.
     */
    private boolean sendViaSmtp(String email, String otp, int expiryMinutes) {
        if (username.isBlank() || password.isBlank()) {
            log.error("[EMAIL CONFIG ERROR] EMAIL_USERNAME and EMAIL_PASSWORD are required for SMTP delivery");
            return false;
        }

        Socket socket = null;
        try {
            boolean useSslDirectly = (port == 465);
            if (useSslDirectly) {
                SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                socket = factory.createSocket(host, port);
            } else {
                socket = new Socket(host, port);
            }
            socket.setSoTimeout(12000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            readResponse(reader); // 220

            sendCommand(writer, "EHLO " + host);
            readEhloResponse(reader);

            if (!useSslDirectly && port == 587) {
                sendCommand(writer, "STARTTLS");
                String startTlsResp = readResponse(reader);
                if (startTlsResp.startsWith("220")) {
                    SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                    socket = factory.createSocket(socket, host, port, true);
                    socket.setSoTimeout(12000);
                    reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

                    sendCommand(writer, "EHLO " + host);
                    readEhloResponse(reader);
                }
            }

            // AUTH LOGIN
            sendCommand(writer, "AUTH LOGIN");
            readResponse(reader); // 334

            sendCommand(writer, Base64.getEncoder().encodeToString(username.getBytes(StandardCharsets.UTF_8)));
            readResponse(reader); // 334

            sendCommand(writer, Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)));
            String authResp = readResponse(reader);
            if (!authResp.startsWith("235")) {
                log.error("[SMTP ERROR] Authentication failed with host {}", host);
                return false;
            }

            // MAIL FROM
            sendCommand(writer, "MAIL FROM:<" + fromEmail + ">");
            readResponse(reader);

            // RCPT TO
            sendCommand(writer, "RCPT TO:<" + email + ">");
            readResponse(reader);

            // DATA
            sendCommand(writer, "DATA");
            readResponse(reader); // 354

            String emailContent = buildMimeMessage(email, otp, expiryMinutes);
            writer.write(emailContent);
            writer.write("\r\n.\r\n");
            writer.flush();
            readResponse(reader); // 250

            sendCommand(writer, "QUIT");

            log.info("[EMAIL SUCCESS] OTP email dispatched via SMTP to {}", maskEmail(email));
            return true;
        } catch (Exception e) {
            log.error("[SMTP ERROR] Failed to send email via SMTP to {}: {}", maskEmail(email), e.getMessage());
            return false;
        } finally {
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private void sendCommand(BufferedWriter writer, String command) throws IOException {
        writer.write(command + "\r\n");
        writer.flush();
    }

    private String readResponse(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) throw new IOException("SMTP connection closed unexpectedly");
        return line;
    }

    private void readEhloResponse(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.length() >= 4 && line.charAt(3) == ' ') {
                break;
            }
        }
    }

    private String buildMimeMessage(String recipientEmail, String otp, int expiryMinutes) {
        return "From: Eventra TN <" + fromEmail + ">\r\n" +
                "To: " + recipientEmail + "\r\n" +
                "Subject: Your Eventra Verification Code\r\n" +
                "MIME-Version: 1.0\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n\r\n" +
                buildHtmlBody(otp, expiryMinutes);
    }

    private String buildHtmlBody(String otp, int expiryMinutes) {
        return "<div style=\"font-family: Arial, sans-serif; max-width: 500px; margin: 0 auto; padding: 24px; border: 1px solid #e2e8f0; border-radius: 12px;\">" +
                "<h2 style=\"color: #168bd0; margin-top: 0;\">Eventra Verification Code</h2>" +
                "<p style=\"color: #4a5568;\">Use the following code to complete your login to Eventra:</p>" +
                "<div style=\"background: #f7fafc; border: 1px solid #cbd5e0; border-radius: 8px; padding: 16px; text-align: center; margin: 24px 0;\">" +
                "<span style=\"font-size: 32px; font-weight: bold; letter-spacing: 6px; color: #2d3748;\">" + otp + "</span>" +
                "</div>" +
                "<p style=\"color: #718096; font-size: 13px;\">This code is valid for <strong>" + expiryMinutes + " minutes</strong>. Do not share this code with anyone.</p>" +
                "<p style=\"color: #a0aec0; font-size: 11px; margin-bottom: 0;\">If you did not request this code, you can safely ignore this email.</p>" +
                "</div>";
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "****";
        int atIndex = email.indexOf('@');
        String name = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (name.length() <= 2) {
            return name.charAt(0) + "***" + domain;
        }
        return name.substring(0, 2) + "***" + name.charAt(name.length() - 1) + domain;
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
