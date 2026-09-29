package com.eventra.backend.controller;

import com.eventra.backend.entity.User;
import com.eventra.backend.repository.UserRepository;
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
        "http://localhost:5190",
        "https://eventra-frontend-theta.vercel.app"
    },
    originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}
)
public class AuthController {

    private final UserRepository userRepository;
    private final OTPService otpService;

    public AuthController(
            UserRepository userRepository,
            OTPService otpService) {

        this.userRepository = userRepository;
        this.otpService = otpService;
    }

    @PostMapping("/register")
    public Map<String, Object> register(
            @RequestBody User user) {

        // Check whether phone already exists
        if (userRepository.existsByPhone(user.getPhone())) {

            return Map.of(
                    "success", false,
                    "message", "Phone number already registered"
            );
        }

        // Save new user
        User savedUser = userRepository.save(user);

        return Map.of(
                "success", true,
                "message", "Registration successful",
                "userId", savedUser.getId(),
                "role", savedUser.getRole()
        );
    }

    @PostMapping("/login")
    public Map<String, Object> login(
            @RequestBody Map<String, String> request) {

        String phone = request.get("phone");
        String otp = request.get("otp");

        if (phone == null || otp == null) {

            return Map.of(
                    "success", false,
                    "message", "Phone number and OTP are required"
            );
        }

        // Verify OTP
        boolean otpValid = otpService.verifyOTP(phone, otp);

        if (!otpValid) {

            return Map.of(
                    "success", false,
                    "message", "Invalid or expired OTP"
            );
        }

        var userOptional = userRepository.findByPhone(phone);

        if (userOptional.isEmpty()) {

            return Map.of(
                    "success", false,
                    "message", "User not registered",
                    "debugPhone", phone
            );
        }
        User user = userOptional.get();

        return Map.of(
                "success", true,
                "message", "Login successful",
                "userId", user.getId(),
                "fullName", user.getFullName(),
                "phone", user.getPhone(),
                "role", user.getRole()
        );
    }
}
