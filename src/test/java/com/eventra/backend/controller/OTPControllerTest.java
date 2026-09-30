package com.eventra.backend.controller;

import com.eventra.backend.dto.OTPVerificationResult;
import com.eventra.backend.dto.SendOtpResult;
import com.eventra.backend.service.OTPService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OTPControllerTest {

    private OTPService otpService;
    private OTPController otpController;

    @BeforeEach
    void setUp() {
        otpService = mock(OTPService.class);
        otpController = new OTPController(otpService);
    }

    @Test
    void testSendOtp_DevelopmentMode_IncludesDemoOtp() {
        when(otpService.sendOTP(eq("9876543210")))
                .thenReturn(SendOtpResult.success("OTP generated successfully (Development Mode)", "123456", 60));

        Map<String, Object> response = otpController.sendOTP(Map.of("phone", "9876543210"));

        assertTrue((Boolean) response.get("success"));
        assertEquals("OTP generated successfully (Development Mode)", response.get("message"));
        assertEquals("123456", response.get("demoOtp"));
    }

    @Test
    void testSendOtp_RealSmsMode_OmitsDemoOtp() {
        // In real SMS mode, demoOtp is null
        when(otpService.sendOTP(eq("9876543210")))
                .thenReturn(SendOtpResult.success("OTP sent successfully to your mobile number", null, 60));

        Map<String, Object> response = otpController.sendOTP(Map.of("phone", "9876543210"));

        assertTrue((Boolean) response.get("success"));
        assertEquals("OTP sent successfully to your mobile number", response.get("message"));
        assertFalse(response.containsKey("demoOtp"), "In real SMS mode, demoOtp must NOT be present in response");
        assertNull(response.get("demoOtp"));
    }

    @Test
    void testSendOtp_MissingPhone() {
        Map<String, Object> response = otpController.sendOTP(Map.of("phone", ""));
        assertFalse((Boolean) response.get("success"));
        assertTrue(response.get("message").toString().contains("required"));
    }

    @Test
    void testVerifyOtp_Success() {
        when(otpService.verifyOTPWithResult(eq("9876543210"), eq("123456")))
                .thenReturn(OTPVerificationResult.success("OTP verified successfully"));

        Map<String, Object> response = otpController.verifyOTP(Map.of("phone", "9876543210", "otp", "123456"));

        assertTrue((Boolean) response.get("success"));
        assertEquals("OTP verified successfully", response.get("message"));
    }

    @Test
    void testVerifyOtp_Failure() {
        when(otpService.verifyOTPWithResult(eq("9876543210"), eq("000000")))
                .thenReturn(OTPVerificationResult.failure("Invalid OTP. Please check the code and try again."));

        Map<String, Object> response = otpController.verifyOTP(Map.of("phone", "9876543210", "otp", "000000"));

        assertFalse((Boolean) response.get("success"));
        assertEquals("Invalid OTP. Please check the code and try again.", response.get("message"));
    }
}
