package com.eventra.backend.controller;

import com.eventra.backend.dto.OTPVerificationResult;
import com.eventra.backend.entity.User;
import com.eventra.backend.repository.UserRepository;
import com.eventra.backend.service.OTPService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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

        if (user == null) {
            return Map.of("success", false, "message", "User details are required");
        }

        boolean hasPhone = user.getPhone() != null && !user.getPhone().isBlank();
        boolean hasEmail = user.getEmail() != null && !user.getEmail().isBlank();

        if (!hasPhone && !hasEmail) {
            return Map.of(
                    "success", false,
                    "message", "Mobile number or email is required"
            );
        }

        // Email validation & duplication check
        if (hasEmail) {
            String normalizedEmail = OTPService.normalizeEmail(user.getEmail());
            if (!OTPService.isValidEmail(normalizedEmail)) {
                return Map.of(
                        "success", false,
                        "message", "Please enter a valid email address"
                );
            }
            user.setEmail(normalizedEmail);
            if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
                return Map.of(
                        "success", false,
                        "message", "Email already registered"
                );
            }
        }

        // Phone validation & duplication check
        if (hasPhone) {
            String normalizedPhone = OTPService.normalizePhone(user.getPhone());
            if (!OTPService.isValidIndianPhone(normalizedPhone)) {
                return Map.of(
                        "success", false,
                        "message", "Please enter a valid 10-digit Indian mobile number"
                );
            }
            user.setPhone(normalizedPhone);
            if (userRepository.existsByPhone(normalizedPhone)) {
                return Map.of(
                        "success", false,
                        "message", "Phone number already registered"
                );
            }
        } else {
            user.setPhone(null);
        }

        // Default display name if none provided
        if (user.getFullName() == null || user.getFullName().isBlank()) {
            boolean isAdmin = user.getRole() != null &&
                    (user.getRole().equalsIgnoreCase("ADMIN") || user.getRole().equalsIgnoreCase("ORGANIZER"));
            user.setFullName(isAdmin ? "Admin / Organizer" : "Participant");
        }

        User savedUser = userRepository.save(user);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Registration successful");
        response.put("userId", savedUser.getId());
        response.put("fullName", savedUser.getFullName());
        if (savedUser.getPhone() != null) response.put("phone", savedUser.getPhone());
        if (savedUser.getEmail() != null) response.put("email", savedUser.getEmail());
        response.put("role", savedUser.getRole());
        return response;
    }

    @PostMapping("/login")
    public Map<String, Object> login(
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

        // 1. Email Login Flow
        if (email != null && !email.isBlank()) {
            String normalizedEmail = OTPService.normalizeEmail(email);
            OTPVerificationResult otpResult = otpService.verifyEmailOTPWithResult(normalizedEmail, otp);

            if (!otpResult.isSuccess()) {
                return Map.of(
                        "success", false,
                        "message", otpResult.getMessage()
                );
            }

            var userOptional = userRepository.findByEmailIgnoreCase(normalizedEmail);
            User user;

            if (userOptional.isPresent()) {
                user = userOptional.get();
            } else {
                // If user doesn't exist yet, auto-provision user so email login succeeds seamlessly
                String requestedRole = request.get("role");
                String role = (requestedRole != null &&
                        (requestedRole.equalsIgnoreCase("ADMIN") || requestedRole.equalsIgnoreCase("ORGANIZER")))
                        ? "ADMIN" : "PARTICIPANT";
                String defaultName = role.equals("ADMIN") ? "Admin / Organizer" : "Participant";

                User newUser = new User();
                newUser.setEmail(normalizedEmail);
                newUser.setFullName(defaultName);
                newUser.setRole(role);
                newUser.setPhone(null);
                user = userRepository.save(newUser);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Login successful");
            response.put("userId", user.getId());
            response.put("fullName", user.getFullName());
            response.put("email", user.getEmail());
            if (user.getPhone() != null && !user.getPhone().isBlank()) {
                response.put("phone", user.getPhone());
            }
            response.put("role", user.getRole());
            return response;
        }

        // 2. Phone Login Flow (existing)
        if (phone == null || phone.isBlank()) {
            return Map.of(
                    "success", false,
                    "message", "Phone number or email is required"
            );
        }

        String normalizedPhone = OTPService.normalizePhone(phone);
        OTPVerificationResult otpResult = otpService.verifyOTPWithResult(normalizedPhone, otp);

        if (!otpResult.isSuccess()) {
            return Map.of(
                    "success", false,
                    "message", otpResult.getMessage()
            );
        }

        var userOptional = userRepository.findByPhone(normalizedPhone);

        if (userOptional.isEmpty()) {
            return Map.of(
                    "success", false,
                    "message", "User not registered"
            );
        }
        User user = userOptional.get();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Login successful");
        response.put("userId", user.getId());
        response.put("fullName", user.getFullName());
        response.put("phone", user.getPhone());
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            response.put("email", user.getEmail());
        }
        response.put("role", user.getRole());
        return response;
    }
}
