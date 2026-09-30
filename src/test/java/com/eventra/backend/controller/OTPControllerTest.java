package com.eventra.backend.controller;

import com.eventra.backend.dto.OTPVerificationResult;
import com.eventra.backend.dto.SendOtpResult;
import com.eventra.backend.entity.User;
import com.eventra.backend.repository.UserRepository;
import com.eventra.backend.service.OTPService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OTPControllerTest {

    private OTPService otpService;
    private UserRepository userRepository;
    private OTPController otpController;

    @BeforeEach
    void setUp() {
        otpService = mock(OTPService.class);
        userRepository = mock(UserRepository.class);
        otpController = new OTPController(otpService, userRepository);
    }

    @Test
    void testSendOtp_Phone_DevelopmentMode_IncludesDemoOtp() {
        when(otpService.sendOTP(eq("9876543210")))
                .thenReturn(SendOtpResult.success("OTP generated successfully (Development Mode)", "123456", 60));

        Map<String, Object> response = otpController.sendOTP(Map.of("phone", "9876543210"));

        assertTrue((Boolean) response.get("success"));
        assertEquals("OTP generated successfully (Development Mode)", response.get("message"));
        assertEquals("123456", response.get("demoOtp"));
    }

    @Test
    void testSendOtp_Phone_RealSmsMode_OmitsDemoOtp() {
        when(otpService.sendOTP(eq("9876543210")))
                .thenReturn(SendOtpResult.success("OTP sent successfully to your mobile number", null, 60));

        Map<String, Object> response = otpController.sendOTP(Map.of("phone", "9876543210"));

        assertTrue((Boolean) response.get("success"));
        assertEquals("OTP sent successfully to your mobile number", response.get("message"));
        assertFalse(response.containsKey("demoOtp"), "In real SMS mode, demoOtp must NOT be present in response");
        assertNull(response.get("demoOtp"));
    }

    @Test
    void testSendOtp_Email_NeverExposesDemoOtp() {
        // Even in dev mode, email sendOTP must NEVER expose demoOtp in response
        when(otpService.sendEmailOTP(eq("user@example.com")))
                .thenReturn(SendOtpResult.success("OTP sent successfully to your email address", null, 60));

        Map<String, Object> response = otpController.sendOTP(Map.of("email", "user@example.com"));

        assertTrue((Boolean) response.get("success"));
        assertEquals("OTP sent successfully to your email address", response.get("message"));
        assertFalse(response.containsKey("demoOtp"), "Email send-otp must NEVER expose demoOtp in response");
    }

    @Test
    void testSendOtp_MissingPhoneAndEmail() {
        Map<String, Object> response = otpController.sendOTP(Map.of("phone", ""));
        assertFalse((Boolean) response.get("success"));
        assertTrue(response.get("message").toString().contains("required"));
    }

    @Test
    void testVerifyOtp_Phone_Success() {
        when(otpService.verifyOTPWithResult(eq("9876543210"), eq("123456")))
                .thenReturn(OTPVerificationResult.success("OTP verified successfully"));

        Map<String, Object> response = otpController.verifyOTP(Map.of("phone", "9876543210", "otp", "123456"));

        assertTrue((Boolean) response.get("success"));
        assertEquals("OTP verified successfully", response.get("message"));
    }

    @Test
    void testVerifyOtp_Phone_Failure() {
        when(otpService.verifyOTPWithResult(eq("9876543210"), eq("000000")))
                .thenReturn(OTPVerificationResult.failure("Invalid OTP. Please check the code and try again."));

        Map<String, Object> response = otpController.verifyOTP(Map.of("phone", "9876543210", "otp", "000000"));

        assertFalse((Boolean) response.get("success"));
        assertEquals("Invalid OTP. Please check the code and try again.", response.get("message"));
    }

    @Test
    void testVerifyOtp_Email_Success() {
        when(otpService.verifyEmailOTPWithResult(eq("user@example.com"), eq("123456")))
                .thenReturn(OTPVerificationResult.success("OTP verified successfully"));

        User user = new User();
        user.setId(10L);
        user.setEmail("user@example.com");
        user.setFullName("Test User");
        user.setRole("PARTICIPANT");
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        Map<String, Object> response = otpController.verifyOTP(Map.of("email", "user@example.com", "otp", "123456"));

        assertTrue((Boolean) response.get("success"));
        assertEquals("OTP verified successfully", response.get("message"));
        assertEquals(10L, response.get("userId"));
        assertEquals("Test User", response.get("fullName"));
        assertEquals("user@example.com", response.get("email"));
        assertEquals("PARTICIPANT", response.get("role"));
    }

    @Test
    void testVerifyOtp_Email_Failure() {
        when(otpService.verifyEmailOTPWithResult(eq("user@example.com"), eq("999999")))
                .thenReturn(OTPVerificationResult.failure("Invalid OTP. Please check the code and try again."));

        Map<String, Object> response = otpController.verifyOTP(Map.of("email", "user@example.com", "otp", "999999"));

        assertFalse((Boolean) response.get("success"));
        assertEquals("Invalid OTP. Please check the code and try again.", response.get("message"));
    }
}
