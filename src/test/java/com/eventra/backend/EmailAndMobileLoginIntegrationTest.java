package com.eventra.backend;

import com.eventra.backend.controller.AuthController;
import com.eventra.backend.controller.OTPController;
import com.eventra.backend.entity.OTPVerification;
import com.eventra.backend.repository.OTPVerificationRepository;
import com.eventra.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EmailAndMobileLoginIntegrationTest {

    @Autowired
    private OTPController otpController;

    @Autowired
    private AuthController authController;

    @Autowired
    private OTPVerificationRepository otpRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        try {
            otpRepository.findTopByEmailOrderByIdDesc("participant.test@eventra.in")
                    .ifPresent(otpRepository::delete);
            otpRepository.findTopByEmailOrderByIdDesc("cooldown.test@eventra.in")
                    .ifPresent(otpRepository::delete);
            otpRepository.findTopByEmailOrderByIdDesc("reuse.test@eventra.in")
                    .ifPresent(otpRepository::delete);
            otpRepository.findTopByEmailOrderByIdDesc("wrongotp.test@eventra.in")
                    .ifPresent(otpRepository::delete);
        } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("3. Test mobile login: mobile number -> demo OTP -> login successful")
    void testMobileLoginFlow() {
        String phone = "9876543210"; // Existing user Kiruba Sri

        // Step 1: Send OTP
        Map<String, Object> sendResp = otpController.sendOTP(Map.of("phone", phone));
        assertTrue((Boolean) sendResp.get("success"));
        assertNotNull(sendResp.get("demoOtp"), "Demo OTP should be returned for mobile login in dev mode");

        String demoOtp = (String) sendResp.get("demoOtp");

        // Step 2: Login with demo OTP
        Map<String, Object> loginResp = authController.login(Map.of("phone", phone, "otp", demoOtp));
        assertTrue((Boolean) loginResp.get("success"));
        assertEquals("Login successful", loginResp.get("message"));
        assertNotNull(loginResp.get("userId"));
        assertEquals("9876543210", loginResp.get("phone"));
        assertEquals("PARTICIPANT", loginResp.get("role"));
    }

    @Test
    @DisplayName("4. Test email login: valid email -> Send OTP -> enter OTP -> login successful")
    void testEmailLoginFlow() {
        String email = "participant.test@eventra.in";

        // Step 1: Send OTP
        Map<String, Object> sendResp = otpController.sendOTP(Map.of("email", email));
        assertTrue((Boolean) sendResp.get("success"));

        // Step 2: Confirm NO demoOtp is returned in email response
        assertFalse(sendResp.containsKey("demoOtp"), "demoOtp MUST NOT be returned in email response");
        assertNull(sendResp.get("demoOtp"));

        // Step 3: Retrieve secure OTP generated in backend database (simulating email delivery reception)
        OTPVerification otpEntity = otpRepository.findTopByEmailOrderByIdDesc(email)
                .orElseThrow(() -> new IllegalStateException("OTP should have been persisted in database"));
        assertNotNull(otpEntity.getOtp());
        assertEquals(6, otpEntity.getOtp().length());
        assertFalse(otpEntity.isUsed());

        // Step 4: Login with OTP
        Map<String, Object> loginResp = authController.login(Map.of("email", email, "otp", otpEntity.getOtp(), "role", "PARTICIPANT"));
        assertTrue((Boolean) loginResp.get("success"));
        assertEquals("Login successful", loginResp.get("message"));
        assertNotNull(loginResp.get("userId"));
        assertEquals(email, loginResp.get("email"));
        assertEquals("PARTICIPANT", loginResp.get("role"));

        // Confirm OTP marked as used
        OTPVerification updatedOtp = otpRepository.findById(otpEntity.getId()).orElseThrow();
        assertTrue(updatedOtp.isUsed(), "OTP must be marked as used after login");
    }

    @Test
    @DisplayName("5. Test wrong OTP rejection")
    void testWrongOtpRejection() {
        String email = "wrongotp.test@eventra.in";

        // Send OTP
        otpController.sendOTP(Map.of("email", email));

        // Attempt verification with incorrect OTP
        Map<String, Object> verifyResp = otpController.verifyOTP(Map.of("email", email, "otp", "000000"));
        assertFalse((Boolean) verifyResp.get("success"));
        assertTrue(verifyResp.get("message").toString().contains("Invalid OTP"));
    }

    @Test
    @DisplayName("6. Test expired OTP rejection")
    void testExpiredOtpRejection() {
        String email = "expired.test@eventra.in";

        // Create expired OTP record directly in database
        OTPVerification expired = new OTPVerification("", email, "123456", LocalDateTime.now().minusMinutes(1));
        expired.setCreatedAt(LocalDateTime.now().minusMinutes(6));
        expired.setUsed(false);
        otpRepository.save(expired);

        // Attempt verify
        Map<String, Object> resp = otpController.verifyOTP(Map.of("email", email, "otp", "123456"));
        assertFalse((Boolean) resp.get("success"));
        assertTrue(resp.get("message").toString().contains("expired"));
    }

    @Test
    @DisplayName("7. Test OTP reuse rejection")
    void testOtpReuseRejection() {
        String email = "reuse.test@eventra.in";

        // Create used OTP record in database
        OTPVerification usedOtp = new OTPVerification("", email, "654321", LocalDateTime.now().plusMinutes(5));
        usedOtp.setUsed(true);
        otpRepository.save(usedOtp);

        // Attempt login with already-used OTP
        Map<String, Object> resp = authController.login(Map.of("email", email, "otp", "654321"));
        assertFalse((Boolean) resp.get("success"));
        assertTrue(resp.get("message").toString().contains("already been used"));
    }

    @Test
    @DisplayName("8. Test resend cooldown")
    void testResendCooldown() {
        String email = "cooldown.test@eventra.in";

        // First send
        Map<String, Object> firstResp = otpController.sendOTP(Map.of("email", email));
        assertTrue((Boolean) firstResp.get("success"));

        // Immediate second send -> must be blocked by 60s cooldown
        Map<String, Object> secondResp = otpController.sendOTP(Map.of("email", email));
        assertFalse((Boolean) secondResp.get("success"));
        assertTrue(secondResp.get("message").toString().contains("Please wait"));
        assertNotNull(secondResp.get("retryAfterSeconds"));
    }

    @Test
    @DisplayName("9. Test invalid email format")
    void testInvalidEmailRejection() {
        Map<String, Object> resp = otpController.sendOTP(Map.of("email", "not-an-email"));
        assertFalse((Boolean) resp.get("success"));
        assertTrue(resp.get("message").toString().contains("valid email"));
    }

    @Test
    @DisplayName("10. Confirm no OTP is exposed in email API responses")
    void testNoOtpExposedInEmailResponses() {
        String email = "noexpose.test@eventra.in";

        Map<String, Object> sendResp = otpController.sendOTP(Map.of("email", email));
        assertFalse(sendResp.containsKey("demoOtp"));
        assertFalse(sendResp.containsKey("otp"));
        assertNull(sendResp.get("demoOtp"));
    }
}
