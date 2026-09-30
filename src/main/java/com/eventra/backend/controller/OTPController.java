package com.eventra.backend.controller;

import com.eventra.backend.dto.OTPVerificationResult;
import com.eventra.backend.dto.SendOtpResult;
import com.eventra.backend.entity.User;
import com.eventra.backend.repository.UserRepository;
import com.eventra.backend.service.OTPService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(
    origins = {
        "http://localhost:5173",
        "http://localhost:5180",
        "http://localhost:5181",
        "http://localhost:5182",
        "http://localhost:5183",
        "http://localhost:5184",
        "http://localhost:5185",
        "http://localhost:5186",
        "http://localhost:5187",
        "http://localhost:5188",
        "http://localhost:5189",
        "http://localhost:5190",
        "https://eventra-frontend-theta.vercel.app"
    },
    originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}
)
public class OTPController {

    private final OTPService otpService;
    private final UserRepository userRepository;

    @Autowired
    public OTPController(OTPService otpService, UserRepository userRepository) {
        this.otpService = otpService;
        this.userRepository = userRepository;
    }

    public OTPController(OTPService otpService) {
        this(otpService, null);
    }

    @PostMapping("/send-otp")
    public Map<String, Object> sendOTP(
            @RequestBody Map<String, String> request) {

        String phone = request != null ? request.get("phone") : null;
        String email = request != null ? request.get("email") : null;

        // Email OTP Send
        if (email != null && !email.isBlank()) {
            SendOtpResult result = otpService.sendEmailOTP(email);

            if (!result.isSuccess()) {
                Map<String, Object> errResponse = new HashMap<>();
                errResponse.put("success", false);
                errResponse.put("message", result.getMessage());
                if (result.getRetryAfterSeconds() > 0) {
                    errResponse.put("retryAfterSeconds", result.getRetryAfterSeconds());
                }
                return errResponse;
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", result.getMessage());
            // Security requirement: NEVER return demoOtp for email
            return response;
        }

        // Mobile OTP Send (existing)
        if (phone == null || phone.isBlank()) {
            return Map.of(
                    "success", false,
                    "message", "Phone number or email is required"
            );
        }

        SendOtpResult result = otpService.sendOTP(phone);

        if (!result.isSuccess()) {
            Map<String, Object> errResponse = new HashMap<>();
            errResponse.put("success", false);
            errResponse.put("message", result.getMessage());
            if (result.getRetryAfterSeconds() > 0) {
                errResponse.put("retryAfterSeconds", result.getRetryAfterSeconds());
            }
            return errResponse;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", result.getMessage());

        // In real SMS mode, demoOtp is null and therefore omitted from response
        if (result.getDemoOtp() != null) {
            response.put("demoOtp", result.getDemoOtp());
        }

        return response;
    }

    @PostMapping("/verify-otp")
    public Map<String, Object> verifyOTP(
            @RequestBody Map<String, String> request) {

        String phone = request != null ? request.get("phone") : null;
        String email = request != null ? request.get("email") : null;
        String otp = request != null ? request.get("otp") : null;

        if (otp == null || otp.isBlank()) {
            return Map.of(
                    "success", false,
                    "message", "OTP is required"
            );
        }

        // Email OTP Verification
        if (email != null && !email.isBlank()) {
            OTPVerificationResult result = otpService.verifyEmailOTPWithResult(email, otp);

            if (!result.isSuccess()) {
                return Map.of(
                        "success", false,
                        "message", result.getMessage()
                );
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", result.getMessage());

            if (userRepository != null) {
                String normalizedEmail = OTPService.normalizeEmail(email);
                Optional<User> userOpt = userRepository.findByEmailIgnoreCase(normalizedEmail);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    response.put("userId", user.getId());
                    response.put("fullName", user.getFullName());
                    response.put("email", user.getEmail());
                    if (user.getPhone() != null && !user.getPhone().isBlank()) {
                        response.put("phone", user.getPhone());
                    }
                    response.put("role", user.getRole());
                }
            }

            return response;
        }

        // Mobile OTP Verification (existing)
        if (phone == null || phone.isBlank()) {
            return Map.of(
                    "success", false,
                    "message", "Phone number or email is required"
            );
        }

        OTPVerificationResult result = otpService.verifyOTPWithResult(phone, otp);

        if (!result.isSuccess()) {
            return Map.of(
                    "success", false,
                    "message", result.getMessage()
            );
        }

        return Map.of(
                "success", true,
                "message", result.getMessage()
        );
    }
}
