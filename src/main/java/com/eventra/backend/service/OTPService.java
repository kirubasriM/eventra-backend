package com.eventra.backend.service;

import com.eventra.backend.dto.OTPVerificationResult;
import com.eventra.backend.dto.SendOtpResult;
import com.eventra.backend.entity.OTPVerification;
import com.eventra.backend.repository.OTPVerificationRepository;
import com.eventra.backend.service.impl.EmailServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class OTPService {

    private static final Logger log = LoggerFactory.getLogger(OTPService.class);

    private final OTPVerificationRepository otpRepository;
    private final SmsService smsService;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    private final int expiryMinutes;
    private final int resendCooldownSeconds;
    private final int maxSendsPerHour;

    @Autowired
    public OTPService(
            OTPVerificationRepository otpRepository,
            SmsService smsService,
            EmailService emailService,
            @Value("${sms.expiry-minutes:5}") int expiryMinutes,
            @Value("${sms.resend-cooldown-seconds:60}") int resendCooldownSeconds,
            @Value("${sms.max-sends-per-hour:5}") int maxSendsPerHour) {

        this.otpRepository = otpRepository;
        this.smsService = smsService;
        this.emailService = emailService != null ? emailService : new EmailServiceImpl(false, "smtp", "", "", "localhost", 25, "no-reply@eventra.in", "");
        this.expiryMinutes = expiryMinutes > 0 ? expiryMinutes : 5;
        this.resendCooldownSeconds = resendCooldownSeconds > 0 ? resendCooldownSeconds : 60;
        this.maxSendsPerHour = maxSendsPerHour > 0 ? maxSendsPerHour : 5;
    }

    public OTPService(
            OTPVerificationRepository otpRepository,
            SmsService smsService,
            int expiryMinutes,
            int resendCooldownSeconds,
            int maxSendsPerHour) {

        this(otpRepository, smsService, new EmailServiceImpl(false, "smtp", "", "", "localhost", 25, "no-reply@eventra.in", ""), expiryMinutes, resendCooldownSeconds, maxSendsPerHour);
    }

    // =========================================================================
    // PHONE OTP METHODS
    // =========================================================================

    /**
     * Normalizes a phone number to standard 10 digits for Indian mobile numbers.
     */
    public static String normalizePhone(String rawPhone) {
        if (rawPhone == null) return "";
        String digits = rawPhone.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return digits;
    }

    /**
     * Validates Indian mobile number format (10 digits starting with 6, 7, 8, or 9).
     */
    public static boolean isValidIndianPhone(String phone) {
        return phone != null && phone.matches("^[6-9]\\d{9}$");
    }

    /**
     * Dispatches a mobile OTP. In real SMS mode, sends via SMS and omits demoOtp.
     * In development mode (OTP_SMS_ENABLED=false), generates and returns demoOtp for testing.
     */
    public SendOtpResult sendOTP(String rawPhone) {
        String phone = normalizePhone(rawPhone);

        if (!isValidIndianPhone(phone)) {
            return SendOtpResult.failure("Please enter a valid 10-digit Indian mobile number");
        }

        // Rate Limit: Max sends per hour
        long recentSends = otpRepository.countByPhoneAndCreatedAtAfter(
                phone, LocalDateTime.now().minusHours(1));
        if (recentSends >= maxSendsPerHour) {
            log.warn("Rate limit exceeded for phone: +91 {}", maskPhone(phone));
            return SendOtpResult.failure("Maximum OTP request limit reached for this hour. Please try again later.");
        }

        // Resend Protection: Check cooldown period on latest OTP
        var latestOtpOpt = otpRepository.findTopByPhoneOrderByIdDesc(phone);
        if (latestOtpOpt.isPresent()) {
            OTPVerification latest = latestOtpOpt.get();
            LocalDateTime createdAt = latest.getCreatedAt();
            if (createdAt == null && latest.getExpiresAt() != null) {
                createdAt = latest.getExpiresAt().minusMinutes(expiryMinutes);
            }
            if (createdAt != null) {
                long elapsedSeconds = Duration.between(createdAt, LocalDateTime.now()).toSeconds();
                if (elapsedSeconds < resendCooldownSeconds && !latest.isUsed()) {
                    long remaining = resendCooldownSeconds - elapsedSeconds;
                    return SendOtpResult.failure(
                            "Please wait " + remaining + " seconds before requesting another OTP.",
                            (int) remaining);
                }
            }
        }

        // Generate cryptographically secure 6-digit OTP
        String otp = String.format("%06d", secureRandom.nextInt(1000000));

        // Real SMS dispatch when enabled
        if (smsService.isSmsEnabled()) {
            boolean dispatched = smsService.sendOtp(phone, otp, expiryMinutes);
            if (!dispatched) {
                return SendOtpResult.failure(
                        "Unable to send SMS at this moment. Please check your mobile number or try again later.");
            }
        }

        // Persist OTP in database
        OTPVerification verification = new OTPVerification();
        verification.setPhone(phone);
        verification.setEmail(null);
        verification.setOtp(otp);
        verification.setCreatedAt(LocalDateTime.now());
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(expiryMinutes));
        verification.setUsed(false);
        verification.setAttempts(0);
        otpRepository.save(verification);

        if (smsService.isSmsEnabled()) {
            log.info("OTP generated and SMS sent to +91 {}", maskPhone(phone));
            return SendOtpResult.success("OTP sent successfully to your mobile number", null, resendCooldownSeconds);
        } else {
            log.info("[DEV MODE] Generated demo OTP for +91 {}: {}", maskPhone(phone), otp);
            return SendOtpResult.success("OTP generated successfully (Development Mode)", otp, resendCooldownSeconds);
        }
    }

    /**
     * Verifies the OTP for a mobile number with detailed status.
     */
    public OTPVerificationResult verifyOTPWithResult(String rawPhone, String rawOtp) {
        String phone = normalizePhone(rawPhone);
        String otp = rawOtp != null ? rawOtp.trim() : "";

        if (!isValidIndianPhone(phone)) {
            return OTPVerificationResult.failure("Invalid mobile number format");
        }

        if (!otp.matches("^\\d{6}$")) {
            return OTPVerificationResult.failure("Please enter a valid 6-digit OTP");
        }

        var latestOtpOpt = otpRepository.findTopByPhoneOrderByIdDesc(phone);

        if (latestOtpOpt.isEmpty()) {
            return OTPVerificationResult.failure("No OTP request found for this mobile number. Please request an OTP first.");
        }

        OTPVerification verification = latestOtpOpt.get();

        if (verification.isUsed()) {
            return OTPVerificationResult.failure("This OTP has already been used. Please request a new OTP.");
        }

        if (LocalDateTime.now().isAfter(verification.getExpiresAt())) {
            return OTPVerificationResult.failure("This OTP has expired. Please request a new OTP.");
        }

        // Brute-force protection: Max 5 attempts per OTP
        if (verification.getAttempts() >= 5) {
            verification.setUsed(true);
            otpRepository.save(verification);
            return OTPVerificationResult.failure("Too many incorrect attempts. Please request a new OTP.");
        }

        if (!verification.getOtp().equals(otp)) {
            verification.setAttempts(verification.getAttempts() + 1);
            otpRepository.save(verification);
            return OTPVerificationResult.failure("Invalid OTP. Please check the code and try again.");
        }

        // Mark OTP as used
        verification.setUsed(true);
        otpRepository.save(verification);

        log.info("OTP verified successfully for +91 {}", maskPhone(phone));
        return OTPVerificationResult.success("OTP verified successfully");
    }

    // =========================================================================
    // EMAIL OTP METHODS
    // =========================================================================

    /**
     * Normalizes email address to trimmed lowercase.
     */
    public static String normalizeEmail(String rawEmail) {
        if (rawEmail == null) return "";
        return rawEmail.trim().toLowerCase();
    }

    /**
     * Validates email format according to standard RFC-like pattern.
     */
    public static boolean isValidEmail(String email) {
        if (email == null) return false;
        String trimmed = email.trim();
        return trimmed.matches("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$");
    }

    /**
     * Dispatches an OTP to the given email address.
     * Generates a secure 6-digit OTP with 5-minute expiry, 60s cooldown, max 5 sends/hour.
     * Never returns demoOtp to frontend, never logs the raw OTP.
     */
    public SendOtpResult sendEmailOTP(String rawEmail) {
        String email = normalizeEmail(rawEmail);

        if (!isValidEmail(email)) {
            return SendOtpResult.failure("Please enter a valid email address");
        }

        // Rate Limit: Max 5 sends per hour per email
        long recentSends = otpRepository.countByEmailAndCreatedAtAfter(
                email, LocalDateTime.now().minusHours(1));
        if (recentSends >= maxSendsPerHour) {
            log.warn("Rate limit exceeded for email: {}", maskEmail(email));
            return SendOtpResult.failure("Maximum OTP request limit reached for this hour. Please try again later.");
        }

        // Resend Protection: 60-second cooldown check
        var latestOtpOpt = otpRepository.findTopByEmailOrderByIdDesc(email);
        if (latestOtpOpt.isPresent()) {
            OTPVerification latest = latestOtpOpt.get();
            LocalDateTime createdAt = latest.getCreatedAt();
            if (createdAt == null && latest.getExpiresAt() != null) {
                createdAt = latest.getExpiresAt().minusMinutes(expiryMinutes);
            }
            if (createdAt != null) {
                long elapsedSeconds = Duration.between(createdAt, LocalDateTime.now()).toSeconds();
                if (elapsedSeconds < resendCooldownSeconds && !latest.isUsed()) {
                    long remaining = resendCooldownSeconds - elapsedSeconds;
                    return SendOtpResult.failure(
                            "Please wait " + remaining + " seconds before requesting another OTP.",
                            (int) remaining);
                }
            }
        }

        // Generate cryptographically secure 6-digit OTP
        String otp = String.format("%06d", secureRandom.nextInt(1000000));

        // Real Email dispatch when enabled
        if (emailService.isEmailEnabled()) {
            boolean dispatched = emailService.sendOtp(email, otp, expiryMinutes);
            if (!dispatched) {
                return SendOtpResult.failure(
                        "Unable to send email at this moment. Please check your email address or try again later.");
            }
        }

        // Persist OTP in database
        OTPVerification verification = new OTPVerification();
        verification.setEmail(email);
        verification.setPhone(""); // Ensures non-null if DB column has NOT NULL constraint
        verification.setOtp(otp);
        verification.setCreatedAt(LocalDateTime.now());
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(expiryMinutes));
        verification.setUsed(false);
        verification.setAttempts(0);
        otpRepository.save(verification);

        // Security rule: Never log the OTP itself
        log.info("OTP generated and dispatched for email: {}", maskEmail(email));

        // Security rule: Never return demoOtp to the frontend for email
        return SendOtpResult.success("OTP sent successfully to your email address", null, resendCooldownSeconds);
    }

    /**
     * Verifies the OTP for an email address with full security validations.
     * Enforces single-use, 5-minute expiry, max 5 verification attempts.
     */
    public OTPVerificationResult verifyEmailOTPWithResult(String rawEmail, String rawOtp) {
        String email = normalizeEmail(rawEmail);
        String otp = rawOtp != null ? rawOtp.trim() : "";

        if (!isValidEmail(email)) {
            return OTPVerificationResult.failure("Invalid email format");
        }

        if (!otp.matches("^\\d{6}$")) {
            return OTPVerificationResult.failure("Please enter a valid 6-digit OTP");
        }

        var latestOtpOpt = otpRepository.findTopByEmailOrderByIdDesc(email);

        if (latestOtpOpt.isEmpty()) {
            return OTPVerificationResult.failure("No OTP request found for this email address. Please request an OTP first.");
        }

        OTPVerification verification = latestOtpOpt.get();

        if (verification.isUsed()) {
            return OTPVerificationResult.failure("This OTP has already been used. Please request a new OTP.");
        }

        if (LocalDateTime.now().isAfter(verification.getExpiresAt())) {
            return OTPVerificationResult.failure("This OTP has expired. Please request a new OTP.");
        }

        // Brute-force protection: Max 5 attempts per OTP
        if (verification.getAttempts() >= 5) {
            verification.setUsed(true);
            otpRepository.save(verification);
            return OTPVerificationResult.failure("Too many incorrect attempts. Please request a new OTP.");
        }

        if (!verification.getOtp().equals(otp)) {
            verification.setAttempts(verification.getAttempts() + 1);
            otpRepository.save(verification);
            return OTPVerificationResult.failure("Invalid OTP. Please check the code and try again.");
        }

        // Mark OTP as used
        verification.setUsed(true);
        otpRepository.save(verification);

        log.info("OTP verified successfully for email {}", maskEmail(email));
        return OTPVerificationResult.success("OTP verified successfully");
    }

    // =========================================================================
    // HELPER & BACKWARD-COMPATIBLE METHODS
    // =========================================================================

    public boolean verifyOTP(String phone, String otp) {
        return verifyOTPWithResult(phone, otp).isSuccess();
    }

    public String generateOTP(String phone) {
        SendOtpResult result = sendOTP(phone);
        if (!result.isSuccess()) {
            throw new IllegalStateException(result.getMessage());
        }
        return result.getDemoOtp() != null ? result.getDemoOtp() : "";
    }

    public boolean isSmsEnabled() {
        return smsService.isSmsEnabled();
    }

    public boolean isEmailEnabled() {
        return emailService.isEmailEnabled();
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return phone.substring(0, 2) + "******" + phone.substring(phone.length() - 2);
    }

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "****";
        int atIndex = email.indexOf('@');
        String name = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (name.length() <= 2) {
            return name.charAt(0) + "***" + domain;
        }
        return name.substring(0, 2) + "***" + name.charAt(name.length() - 1) + domain;
    }
}
