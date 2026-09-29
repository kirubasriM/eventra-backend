package com.eventra.backend.controller;

import com.eventra.backend.entity.User;
import com.eventra.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
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
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // 1. GET /api/users/{id} - Get user profile by ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        if (id == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "User ID is required"));
        }

        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "User not found with ID: " + id));
        }

        User user = userOpt.get();
        return ResponseEntity.ok(user);
    }

    // 2. PUT /api/users/{id} - Update allowed profile fields
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        if (id == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "User ID is required"));
        }

        if (updates == null || updates.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No profile data provided"));
        }

        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "User not found with ID: " + id));
        }

        User user = userOpt.get();

        // Update allowed profile fields only:
        // Full Name
        if (updates.containsKey("fullName")) {
            Object val = updates.get("fullName");
            user.setFullName(val != null ? val.toString().trim() : "");
        } else if (updates.containsKey("name")) {
            Object val = updates.get("name");
            user.setFullName(val != null ? val.toString().trim() : "");
        }

        // Email
        if (updates.containsKey("email")) {
            Object val = updates.get("email");
            user.setEmail(val != null ? val.toString().trim().toLowerCase() : "");
        }

        // College
        if (updates.containsKey("college")) {
            Object val = updates.get("college");
            user.setCollege(val != null ? val.toString().trim() : "");
        }

        // Department
        if (updates.containsKey("department")) {
            Object val = updates.get("department");
            user.setDepartment(val != null ? val.toString().trim() : "");
        }

        // Year
        if (updates.containsKey("year")) {
            Object val = updates.get("year");
            user.setYear(val != null ? val.toString().trim() : "");
        }

        // City
        if (updates.containsKey("city")) {
            Object val = updates.get("city");
            user.setCity(val != null ? val.toString().trim() : "");
        }

        // Designation (if provided)
        if (updates.containsKey("designation")) {
            Object val = updates.get("designation");
            user.setDesignation(val != null ? val.toString().trim() : "");
        }

        // STRICT SAFETY: Do NOT modify phone or role
        // user.getPhone() and user.getRole() remain unchanged

        User savedUser = userRepository.save(user);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Profile updated successfully");
        response.put("user", savedUser);
        response.put("id", savedUser.getId());
        response.put("fullName", savedUser.getFullName());
        response.put("name", savedUser.getFullName());
        response.put("phone", savedUser.getPhone());
        response.put("email", savedUser.getEmail());
        response.put("role", savedUser.getRole());
        response.put("college", savedUser.getCollege());
        response.put("department", savedUser.getDepartment());
        response.put("year", savedUser.getYear());
        response.put("city", savedUser.getCity());
        response.put("designation", savedUser.getDesignation());

        return ResponseEntity.ok(response);
    }
}
