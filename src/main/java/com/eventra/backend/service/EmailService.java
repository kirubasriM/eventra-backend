package com.eventra.backend.service;

public interface EmailService {

    /**
     * Dispatches an OTP to the specified recipient email address.
     *
     * @param email         Normalized recipient email address
     * @param otp           6-digit OTP string
     * @param expiryMinutes Validity duration in minutes
     * @return true if successfully accepted/dispatched by the email service, false otherwise
     */
    boolean sendOtp(String email, String otp, int expiryMinutes);

    /**
     * Checks if real email sending is enabled via configuration.
     *
     * @return true if EMAIL_ENABLED=true
     */
    boolean isEmailEnabled();
}
