package com.eventra.backend.dto;

public class OTPVerificationResult {

    private final boolean success;
    private final String message;

    public OTPVerificationResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static OTPVerificationResult success(String message) {
        return new OTPVerificationResult(true, message);
    }

    public static OTPVerificationResult failure(String message) {
        return new OTPVerificationResult(false, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
