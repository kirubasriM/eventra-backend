package com.eventra.backend.controller;

import com.eventra.backend.dto.OTPVerificationResult;
import com.eventra.backend.entity.User;
import com.eventra.backend.repository.UserRepository;
import com.eventra.backend.service.OTPService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthControllerTest {

    private UserRepository userRepository;
    private OTPService otpService;
    private AuthController authController;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        otpService = mock(OTPService.class);
        authController = new AuthController(userRepository, otpService);
    }

    @Test
    void testLogin_Email_ExistingUser_Success() {
        when(otpService.verifyEmailOTPWithResult(eq("user@example.com"), eq("123456")))
                .thenReturn(OTPVerificationResult.success("OTP verified successfully"));

        User existingUser = new User();
        existingUser.setId(5L);
        existingUser.setFullName("Existing User");
        existingUser.setEmail("user@example.com");
        existingUser.setRole("PARTICIPANT");

        when(userRepository.findByEmailIgnoreCase("user@example.com"))
                .thenReturn(Optional.of(existingUser));

        Map<String, Object> response = authController.login(Map.of(
                "email", "user@example.com",
                "otp", "123456"
        ));

        assertTrue((Boolean) response.get("success"));
        assertEquals("Login successful", response.get("message"));
        assertEquals(5L, response.get("userId"));
        assertEquals("Existing User", response.get("fullName"));
        assertEquals("user@example.com", response.get("email"));
        assertEquals("PARTICIPANT", response.get("role"));
    }

    @Test
    void testLogin_Email_NewUser_AutoProvisionsAndSucceeds() {
        when(otpService.verifyEmailOTPWithResult(eq("newuser@example.com"), eq("123456")))
                .thenReturn(OTPVerificationResult.success("OTP verified successfully"));

        when(userRepository.findByEmailIgnoreCase("newuser@example.com"))
                .thenReturn(Optional.empty());

        User savedUser = new User();
        savedUser.setId(99L);
        savedUser.setEmail("newuser@example.com");
        savedUser.setFullName("Participant");
        savedUser.setRole("PARTICIPANT");

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        Map<String, Object> response = authController.login(Map.of(
                "email", "newuser@example.com",
                "otp", "123456",
                "role", "PARTICIPANT"
        ));

        assertTrue((Boolean) response.get("success"));
        assertEquals("Login successful", response.get("message"));
        assertEquals(99L, response.get("userId"));
        assertEquals("newuser@example.com", response.get("email"));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testLogin_Email_WrongOtp_Fails() {
        when(otpService.verifyEmailOTPWithResult(eq("user@example.com"), eq("000000")))
                .thenReturn(OTPVerificationResult.failure("Invalid OTP. Please check the code and try again."));

        Map<String, Object> response = authController.login(Map.of(
                "email", "user@example.com",
                "otp", "000000"
        ));

        assertFalse((Boolean) response.get("success"));
        assertEquals("Invalid OTP. Please check the code and try again.", response.get("message"));
        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    void testRegister_Email_Success() {
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);

        User saved = new User();
        saved.setId(101L);
        saved.setEmail("new@example.com");
        saved.setFullName("Participant");
        saved.setRole("PARTICIPANT");
        when(userRepository.save(any(User.class))).thenReturn(saved);

        User toRegister = new User();
        toRegister.setEmail("new@example.com");
        toRegister.setRole("PARTICIPANT");

        Map<String, Object> response = authController.register(toRegister);

        assertTrue((Boolean) response.get("success"));
        assertEquals("Registration successful", response.get("message"));
        assertEquals(101L, response.get("userId"));
    }
}
