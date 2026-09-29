package com.eventra.backend.controller;

import com.eventra.backend.entity.Event;
import com.eventra.backend.entity.EventRegistration;
import com.eventra.backend.entity.User;
import com.eventra.backend.repository.EventRegistrationRepository;
import com.eventra.backend.repository.EventRepository;
import com.eventra.backend.repository.UserRepository;
import com.eventra.backend.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/registrations")
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
public class EventRegistrationController {

    private final EventRegistrationRepository eventRegistrationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public EventRegistrationController(
            EventRegistrationRepository eventRegistrationRepository,
            EventRepository eventRepository,
            UserRepository userRepository,
            NotificationService notificationService) {
        this.eventRegistrationRepository = eventRegistrationRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    // 1. POST /api/registrations - Register a participant for an event
    @PostMapping
    public ResponseEntity<?> registerParticipant(@RequestBody EventRegistration registration) {
        if (registration == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Registration data is required"));
        }

        if (registration.getEventId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Event ID is required"));
        }

        if (registration.getUserId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "User ID is required"));
        }

        if (registration.getStudentName() == null || registration.getStudentName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Student name is required"));
        }

        if (registration.getMobile() == null || registration.getMobile().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Mobile number is required"));
        }

        // Check that event exists
        Optional<Event> eventOpt = eventRepository.findById(registration.getEventId());
        if (eventOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Event not found with ID: " + registration.getEventId()));
        }

        // Check that user exists
        Optional<User> userOpt = userRepository.findById(registration.getUserId());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "User not found with ID: " + registration.getUserId()));
        }

        // Prevent duplicate registration for the same event and user
        boolean alreadyRegistered = eventRegistrationRepository.existsByEventIdAndUserId(
                registration.getEventId(), registration.getUserId());
        if (alreadyRegistered) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "You are already registered for this event"));
        }

        // Generate a unique registrationId
        String uniqueRegId = "REG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        registration.setRegistrationId(uniqueRegId);

        // Set registrationDate automatically
        registration.setRegistrationDate(LocalDateTime.now());

        // Set status to REGISTERED
        registration.setStatus("REGISTERED");

        EventRegistration saved = eventRegistrationRepository.save(registration);

        Event event = eventOpt.get();

        // Trigger 1: Successful event registration notification for participant
        try {
            notificationService.sendNotification(
                    saved.getUserId(),
                    "Registration Successful",
                    "You have successfully registered for " + event.getTitle() + ".",
                    "REGISTRATION",
                    saved.getEventId()
            );
        } catch (Exception e) {
            System.err.println("Failed to create participant notification: " + e.getMessage());
        }

        // Trigger 4: New participant registration notification for organizer/admin
        try {
            Long organizerId = event.getCreatedBy();
            if (organizerId != null) {
                notificationService.sendNotification(
                        organizerId,
                        "New Event Registration",
                        saved.getStudentName() + " has registered for " + event.getTitle() + ".",
                        "ORGANIZER_ALERT",
                        saved.getEventId()
                );
            }
        } catch (Exception e) {
            System.err.println("Failed to create organizer notification: " + e.getMessage());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // 2. GET /api/registrations/user/{userId} - Return all registrations belonging to that user
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<EventRegistration>> getRegistrationsByUserId(@PathVariable Long userId) {
        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }
        List<EventRegistration> registrations = eventRegistrationRepository.findByUserId(userId);
        return ResponseEntity.ok(registrations);
    }

    // 3. GET /api/registrations/event/{eventId} - Return all registrations for a specific event
    @GetMapping("/event/{eventId}")
    public ResponseEntity<List<EventRegistration>> getRegistrationsByEventId(@PathVariable Long eventId) {
        if (eventId == null) {
            return ResponseEntity.badRequest().build();
        }
        List<EventRegistration> registrations = eventRegistrationRepository.findByEventId(eventId);
        return ResponseEntity.ok(registrations);
    }

    // 4. GET /api/registrations/{registrationId} - Return one registration using registrationId
    @GetMapping("/{registrationId}")
    public ResponseEntity<?> getRegistrationById(@PathVariable String registrationId) {
        if (registrationId == null || registrationId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Registration ID is required"));
        }

        Optional<EventRegistration> regOpt = eventRegistrationRepository.findByRegistrationId(registrationId.trim());
        if (regOpt.isPresent()) {
            return ResponseEntity.ok(regOpt.get());
        }

        // Try numeric ID fallback if given
        try {
            Long numericId = Long.parseLong(registrationId.trim());
            Optional<EventRegistration> byNumericId = eventRegistrationRepository.findById(numericId);
            if (byNumericId.isPresent()) {
                return ResponseEntity.ok(byNumericId.get());
            }
        } catch (NumberFormatException ignored) {
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Registration not found with ID: " + registrationId));
    }
}
