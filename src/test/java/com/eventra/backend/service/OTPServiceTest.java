package com.eventra.backend.service;

import com.eventra.backend.dto.OTPVerificationResult;
import com.eventra.backend.dto.SendOtpResult;
import com.eventra.backend.entity.OTPVerification;
import com.eventra.backend.repository.OTPVerificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OTPServiceTest {

    private OTPVerificationRepository otpRepository;
    private SmsService smsService;
    private EmailService emailService;
    private OTPService otpService;

    @BeforeEach
    void setUp() {
        otpRepository = mock(OTPVerificationRepository.class);
        smsService = mock(SmsService.class);
        emailService = mock(EmailService.class);
        // expiry: 5 mins, cooldown: 60 secs, max sends: 5 per hour
        otpService = new OTPService(otpRepository, smsService, emailService, 5, 60, 5);
    }

    // =========================================================================
    // PHONE OTP TESTS
    // =========================================================================

    @Test
    void testNormalizePhone() {
        assertEquals("9876543210", OTPService.normalizePhone("+919876543210"));
        assertEquals("9876543210", OTPService.normalizePhone("919876543210"));
        assertEquals("9876543210", OTPService.normalizePhone("09876543210"));
        assertEquals("9876543210", OTPService.normalizePhone("98765 43210"));
        assertEquals("9876543210", OTPService.normalizePhone("98765-43210"));
    }

    @Test
    void testIsValidIndianPhone() {
        assertTrue(OTPService.isValidIndianPhone("9876543210"));
        assertTrue(OTPService.isValidIndianPhone("6123456789"));
        assertTrue(OTPService.isValidIndianPhone("7890123456"));
        assertTrue(OTPService.isValidIndianPhone("8901234567"));

        assertFalse(OTPService.isValidIndianPhone("5123456789")); // starts with 5
        assertFalse(OTPService.isValidIndianPhone("987654321"));  // 9 digits
        assertFalse(OTPService.isValidIndianPhone("98765432100")); // 11 digits
        assertFalse(OTPService.isValidIndianPhone(""));
        assertFalse(OTPService.isValidIndianPhone(null));
    }

    @Test
    void testSendOtp_DevelopmentMode_ReturnsDemoOtp() {
        when(smsService.isSmsEnabled()).thenReturn(false);
        when(otpRepository.countByPhoneAndCreatedAtAfter(anyString(), any())).thenReturn(0L);
        when(otpRepository.findTopByPhoneOrderByIdDesc(anyString())).thenReturn(Optional.empty());

        SendOtpResult result = otpService.sendOTP("+91 98765 43210");

        assertTrue(result.isSuccess());
        assertNotNull(result.getDemoOtp());
        assertEquals(6, result.getDemoOtp().length());
        assertTrue(result.getDemoOtp().matches("^\\d{6}$"));
        verify(smsService, never()).sendOtp(anyString(), anyString(), anyInt());
        verify(otpRepository).save(any(OTPVerification.class));
    }

    @Test
    void testSendOtp_RealSmsMode_OmitsDemoOtp() {
        when(smsService.isSmsEnabled()).thenReturn(true);
        when(smsService.sendOtp(eq("9876543210"), anyString(), eq(5))).thenReturn(true);
        when(otpRepository.countByPhoneAndCreatedAtAfter(anyString(), any())).thenReturn(0L);
        when(otpRepository.findTopByPhoneOrderByIdDesc(anyString())).thenReturn(Optional.empty());

        SendOtpResult result = otpService.sendOTP("9876543210");

        assertTrue(result.isSuccess());
        assertNull(result.getDemoOtp(), "In real SMS mode, demoOtp must be null/omitted");
        verify(smsService).sendOtp(eq("9876543210"), anyString(), eq(5));
        verify(otpRepository).save(any(OTPVerification.class));
    }

    @Test
    void testSendOtp_ResendCooldown_BlocksRapidRequests() {
        when(smsService.isSmsEnabled()).thenReturn(false);
        when(otpRepository.countByPhoneAndCreatedAtAfter(anyString(), any())).thenReturn(0L);

        OTPVerification recent = new OTPVerification("9876543210", "123456", LocalDateTime.now().plusMinutes(5));
        recent.setCreatedAt(LocalDateTime.now().minusSeconds(15)); // 15 seconds ago
        recent.setUsed(false);

        when(otpRepository.findTopByPhoneOrderByIdDesc("9876543210")).thenReturn(Optional.of(recent));

        SendOtpResult result = otpService.sendOTP("9876543210");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Please wait"));
        assertTrue(result.getRetryAfterSeconds() > 0 && result.getRetryAfterSeconds() <= 45);
        verify(otpRepository, never()).save(any(OTPVerification.class));
    }

    @Test
    void testSendOtp_RateLimitPerHour_BlocksAbuse() {
        when(smsService.isSmsEnabled()).thenReturn(false);
        // Already sent 5 times in the last hour
        when(otpRepository.countByPhoneAndCreatedAtAfter(eq("9876543210"), any())).thenReturn(5L);

        SendOtpResult result = otpService.sendOTP("9876543210");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("limit reached"));
        verify(otpRepository, never()).save(any(OTPVerification.class));
    }

    @Test
    void testVerifyOtp_Success_MarksAsUsed() {
        OTPVerification verification = new OTPVerification("9876543210", "654321", LocalDateTime.now().plusMinutes(5));
        verification.setUsed(false);
        when(otpRepository.findTopByPhoneOrderByIdDesc("9876543210")).thenReturn(Optional.of(verification));

        OTPVerificationResult result = otpService.verifyOTPWithResult("9876543210", "654321");

        assertTrue(result.isSuccess());
        assertTrue(verification.isUsed());
        verify(otpRepository).save(verification);
    }

    @Test
    void testVerifyOtp_AlreadyUsed_Fails() {
        OTPVerification verification = new OTPVerification("9876543210", "654321", LocalDateTime.now().plusMinutes(5));
        verification.setUsed(true);
        when(otpRepository.findTopByPhoneOrderByIdDesc("9876543210")).thenReturn(Optional.of(verification));

        OTPVerificationResult result = otpService.verifyOTPWithResult("9876543210", "654321");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("already been used"));
    }

    @Test
    void testVerifyOtp_Expired_Fails() {
        OTPVerification verification = new OTPVerification("9876543210", "654321", LocalDateTime.now().minusSeconds(1));
        verification.setUsed(false);
        when(otpRepository.findTopByPhoneOrderByIdDesc("9876543210")).thenReturn(Optional.of(verification));

        OTPVerificationResult result = otpService.verifyOTPWithResult("9876543210", "654321");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("expired"));
    }

    @Test
    void testVerifyOtp_WrongOtp_IncrementsAttemptsAndFails() {
        OTPVerification verification = new OTPVerification("9876543210", "654321", LocalDateTime.now().plusMinutes(5));
        verification.setUsed(false);
        verification.setAttempts(1);
        when(otpRepository.findTopByPhoneOrderByIdDesc("9876543210")).thenReturn(Optional.of(verification));

        OTPVerificationResult result = otpService.verifyOTPWithResult("9876543210", "111111");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Invalid OTP"));
        assertEquals(2, verification.getAttempts());
        assertFalse(verification.isUsed());
        verify(otpRepository).save(verification);
    }

    @Test
    void testVerifyOtp_MaxAttemptsExceeded_LocksOtp() {
        OTPVerification verification = new OTPVerification("9876543210", "654321", LocalDateTime.now().plusMinutes(5));
        verification.setUsed(false);
        verification.setAttempts(5);
        when(otpRepository.findTopByPhoneOrderByIdDesc("9876543210")).thenReturn(Optional.of(verification));

        OTPVerificationResult result = otpService.verifyOTPWithResult("9876543210", "654321");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Too many incorrect attempts"));
        assertTrue(verification.isUsed());
        verify(otpRepository).save(verification);
    }

    // =========================================================================
    // EMAIL OTP TESTS
    // =========================================================================

    @Test
    void testNormalizeEmail() {
        assertEquals("user@example.com", OTPService.normalizeEmail("  USER@EXAMPLE.COM  "));
        assertEquals("test.dev@eventra.in", OTPService.normalizeEmail("Test.Dev@Eventra.IN"));
        assertEquals("", OTPService.normalizeEmail(null));
    }

    @Test
    void testIsValidEmail() {
        assertTrue(OTPService.isValidEmail("user@example.com"));
        assertTrue(OTPService.isValidEmail("kiruba.sri@eventra.in"));
        assertTrue(OTPService.isValidEmail("test+filter@sub.domain.co"));

        assertFalse(OTPService.isValidEmail(""));
        assertFalse(OTPService.isValidEmail("invalid-email"));
        assertFalse(OTPService.isValidEmail("missing@domain"));
        assertFalse(OTPService.isValidEmail("@missingusername.com"));
        assertFalse(OTPService.isValidEmail(null));
    }

    @Test
    void testSendEmailOtp_NeverReturnsDemoOtp() {
        when(emailService.isEmailEnabled()).thenReturn(false);
        when(otpRepository.countByEmailAndCreatedAtAfter(anyString(), any())).thenReturn(0L);
        when(otpRepository.findTopByEmailOrderByIdDesc(anyString())).thenReturn(Optional.empty());

        SendOtpResult result = otpService.sendEmailOTP("User@Example.Com");

        assertTrue(result.isSuccess());
        // CRITICAL REQUIREMENT: demoOtp must ALWAYS be null for email
        assertNull(result.getDemoOtp(), "Email OTP must NEVER expose demoOtp in response");
        verify(otpRepository).save(any(OTPVerification.class));
    }

    @Test
    void testSendEmailOtp_RealEmailDispatch() {
        when(emailService.isEmailEnabled()).thenReturn(true);
        when(emailService.sendOtp(eq("user@example.com"), anyString(), eq(5))).thenReturn(true);
        when(otpRepository.countByEmailAndCreatedAtAfter(anyString(), any())).thenReturn(0L);
        when(otpRepository.findTopByEmailOrderByIdDesc(anyString())).thenReturn(Optional.empty());

        SendOtpResult result = otpService.sendEmailOTP("user@example.com");

        assertTrue(result.isSuccess());
        assertNull(result.getDemoOtp());
        verify(emailService).sendOtp(eq("user@example.com"), anyString(), eq(5));
        verify(otpRepository).save(any(OTPVerification.class));
    }

    @Test
    void testSendEmailOtp_ResendCooldown_BlocksRapidRequests() {
        when(emailService.isEmailEnabled()).thenReturn(false);
        when(otpRepository.countByEmailAndCreatedAtAfter(anyString(), any())).thenReturn(0L);

        OTPVerification recent = new OTPVerification("", "user@example.com", "123456", LocalDateTime.now().plusMinutes(5));
        recent.setCreatedAt(LocalDateTime.now().minusSeconds(20)); // 20s ago
        recent.setUsed(false);

        when(otpRepository.findTopByEmailOrderByIdDesc("user@example.com")).thenReturn(Optional.of(recent));

        SendOtpResult result = otpService.sendEmailOTP("user@example.com");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Please wait"));
        assertTrue(result.getRetryAfterSeconds() > 0 && result.getRetryAfterSeconds() <= 40);
        verify(otpRepository, never()).save(any(OTPVerification.class));
    }

    @Test
    void testSendEmailOtp_RateLimitPerHour_BlocksAbuse() {
        when(emailService.isEmailEnabled()).thenReturn(false);
        // Already sent 5 times in the last hour
        when(otpRepository.countByEmailAndCreatedAtAfter(eq("user@example.com"), any())).thenReturn(5L);

        SendOtpResult result = otpService.sendEmailOTP("user@example.com");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("limit reached"));
        verify(otpRepository, never()).save(any(OTPVerification.class));
    }

    @Test
    void testSendEmailOtp_InvalidEmail_Fails() {
        SendOtpResult result = otpService.sendEmailOTP("not-an-email");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("valid email"));
        verify(otpRepository, never()).save(any());
    }

    @Test
    void testVerifyEmailOtp_Success_MarksAsUsed() {
        OTPVerification verification = new OTPVerification("", "user@example.com", "654321", LocalDateTime.now().plusMinutes(5));
        verification.setUsed(false);
        when(otpRepository.findTopByEmailOrderByIdDesc("user@example.com")).thenReturn(Optional.of(verification));

        OTPVerificationResult result = otpService.verifyEmailOTPWithResult("user@example.com", "654321");

        assertTrue(result.isSuccess());
        assertTrue(verification.isUsed());
        verify(otpRepository).save(verification);
    }

    @Test
    void testVerifyEmailOtp_AlreadyUsed_Fails() {
        OTPVerification verification = new OTPVerification("", "user@example.com", "654321", LocalDateTime.now().plusMinutes(5));
        verification.setUsed(true);
        when(otpRepository.findTopByEmailOrderByIdDesc("user@example.com")).thenReturn(Optional.of(verification));

        OTPVerificationResult result = otpService.verifyEmailOTPWithResult("user@example.com", "654321");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("already been used"));
    }

    @Test
    void testVerifyEmailOtp_Expired_Fails() {
        OTPVerification verification = new OTPVerification("", "user@example.com", "654321", LocalDateTime.now().minusSeconds(5));
        verification.setUsed(false);
        when(otpRepository.findTopByEmailOrderByIdDesc("user@example.com")).thenReturn(Optional.of(verification));

        OTPVerificationResult result = otpService.verifyEmailOTPWithResult("user@example.com", "654321");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("expired"));
    }

    @Test
    void testVerifyEmailOtp_WrongOtp_IncrementsAttemptsAndFails() {
        OTPVerification verification = new OTPVerification("", "user@example.com", "654321", LocalDateTime.now().plusMinutes(5));
        verification.setUsed(false);
        verification.setAttempts(1);
        when(otpRepository.findTopByEmailOrderByIdDesc("user@example.com")).thenReturn(Optional.of(verification));

        OTPVerificationResult result = otpService.verifyEmailOTPWithResult("user@example.com", "000000");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Invalid OTP"));
        assertEquals(2, verification.getAttempts());
        assertFalse(verification.isUsed());
        verify(otpRepository).save(verification);
    }

    @Test
    void testVerifyEmailOtp_MaxAttemptsExceeded_LocksOtp() {
        OTPVerification verification = new OTPVerification("", "user@example.com", "654321", LocalDateTime.now().plusMinutes(5));
        verification.setUsed(false);
        verification.setAttempts(5);
        when(otpRepository.findTopByEmailOrderByIdDesc("user@example.com")).thenReturn(Optional.of(verification));

        OTPVerificationResult result = otpService.verifyEmailOTPWithResult("user@example.com", "654321");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Too many incorrect attempts"));
        assertTrue(verification.isUsed());
        verify(otpRepository).save(verification);
    }

    @Test
    void testMaskEmail() {
        assertEquals("ki***a@example.com", OTPService.maskEmail("kiruba@example.com"));
        assertEquals("a***@c.com", OTPService.maskEmail("ab@c.com"));
        assertEquals("****", OTPService.maskEmail("invalid"));
    }
}
