package com.eventra.backend.dto;

public class SendOtpResult {

    private final boolean success;
    private final String message;
    private final String demoOtp;
    private final int retryAfterSeconds;

    public SendOtpResult(boolean success, String message, String demoOtp, int retryAfterSeconds) {
        this.success = success;
        this.message = message;
        this.demoOtp = demoOtp;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static SendOtpResult success(String message, String demoOtp, int retryAfterSeconds) {
        return new SendOtpResult(true, message, demoOtp, retryAfterSeconds);
    }

    public static SendOtpResult failure(String message, int retryAfterSeconds) {
        return new SendOtpResult(false, message, null, retryAfterSeconds);
    }

    public static SendOtpResult failure(String message) {
        return new SendOtpResult(false, message, null, 0);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getDemoOtp() {
        return demoOtp;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
