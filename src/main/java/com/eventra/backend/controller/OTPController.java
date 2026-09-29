package com.eventra.backend.controller;

import com.eventra.backend.service.OTPService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
        "http://localhost:5190"
    },
    originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}
)
public class OTPController {

    private final OTPService otpService;

    public OTPController(OTPService otpService) {
        this.otpService = otpService;
    }

    @PostMapping("/send-otp")
    public Map<String, Object> sendOTP(
            @RequestBody Map<String, String> request) {

        String phone = request.get("phone");

        if (phone == null || phone.isBlank()) {
            return Map.of(
                    "success", false,
                    "message", "Phone number is required"
            );
        }

        String otp = otpService.generateOTP(phone);

        return Map.of(
                "success", true,
                "message", "OTP generated successfully",
                "demoOtp", otp
        );
    }

    @PostMapping("/verify-otp")
    public Map<String, Object> verifyOTP(
            @RequestBody Map<String, String> request) {

        String phone = request.get("phone");
        String otp = request.get("otp");

        if (phone == null || phone.isBlank()
                || otp == null || otp.isBlank()) {

            return Map.of(
                    "success", false,
                    "message", "Phone number and OTP are required"
            );
        }

        boolean valid = otpService.verifyOTP(phone, otp);

        if (!valid) {
            return Map.of(
                    "success", false,
                    "message", "Invalid or expired OTP"
            );
        }

        return Map.of(
                "success", true,
                "message", "OTP verified successfully"
        );
    }
}
