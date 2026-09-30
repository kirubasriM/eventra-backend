package com.eventra.backend.service;

public interface SmsService {

    /**
     * Dispatches an OTP via SMS to the specified recipient.
     *
     * @param phone         10-digit normalized Indian mobile number (e.g. 9876543210)
     * @param otp           6-digit OTP string
     * @param expiryMinutes Validity in minutes
     * @return true if successfully accepted/sent by provider, false otherwise
     */
    boolean sendOtp(String phone, String otp, int expiryMinutes);

    /**
     * Checks if real SMS sending is enabled via configuration.
     *
     * @return true if OTP_SMS_ENABLED=true
     */
    boolean isSmsEnabled();
}
