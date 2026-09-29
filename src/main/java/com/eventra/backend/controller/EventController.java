package com.eventra.backend.controller;

import com.eventra.backend.entity.Event;
import com.eventra.backend.entity.EventRegistration;
import com.eventra.backend.repository.EventRegistrationRepository;
import com.eventra.backend.repository.EventRepository;
import com.eventra.backend.service.NotificationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/events")
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
public class EventController {

    private final EventRepository eventRepository;
    private final NotificationService notificationService;
    private final EventRegistrationRepository eventRegistrationRepository;

    public EventController(
            EventRepository eventRepository,
            NotificationService notificationService,
            EventRegistrationRepository eventRegistrationRepository) {
        this.eventRepository = eventRepository;
        this.notificationService = notificationService;
        this.eventRegistrationRepository = eventRegistrationRepository;
    }

    // 1. POST /api/events - Create a new event
    @PostMapping
    public ResponseEntity<?> createEvent(@RequestBody Event event) {
        if (event == null) {
            return ResponseEntity.badRequest().body("Event data is required");
        }
        if (event.getTitle() == null || event.getTitle().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Title is required");
        }
        if (event.getCategory() == null || event.getCategory().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Category is required");
        }
        if (event.getEventDate() == null) {
            return ResponseEntity.badRequest().body("Event date is required");
        }

        // Set default status if not provided, or uppercase (e.g. DRAFT, PUBLISHED)
        if (event.getStatus() == null || event.getStatus().trim().isEmpty()) {
            event.setStatus("PUBLISHED");
        } else {
            event.setStatus(event.getStatus().trim().toUpperCase());
        }

        // Set default createdBy if not provided
        if (event.getCreatedBy() == null) {
            event.setCreatedBy(1L);
        }

        Event savedEvent = eventRepository.save(event);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedEvent);
    }

    // 2. GET /api/events - Return all events
    @GetMapping
    public List<Event> getAllEvents() {
        return eventRepository.findAll();
    }

    // 3. GET /api/events/cities - Return distinct cities with events
    @GetMapping("/cities")
    public List<String> getCities() {
        return eventRepository.findDistinctCities();
    }

    // 4. GET /api/events/created-by/{userId} - Return events created by a specific admin/organizer
    @GetMapping("/created-by/{userId}")
    public ResponseEntity<?> getEventsByCreator(@PathVariable Long userId) {
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "User ID is required"));
        }
        List<Event> events = eventRepository.findByCreatedByOrderByIdDesc(userId);
        return ResponseEntity.ok(events);
    }

    // 5. GET /api/events/search - Search events using title/category/city/date
    @GetMapping("/search")
    public List<Event> searchEvents(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
        boolean hasCategory = category != null && !category.trim().isEmpty() && !"all".equalsIgnoreCase(category.trim());
        boolean hasCity = city != null && !city.trim().isEmpty() && !"all".equalsIgnoreCase(city.trim());
        boolean hasDate = date != null;

        if (!hasKeyword && !hasCategory && !hasCity && !hasDate) {
            return eventRepository.findAll();
        }

        return eventRepository.filterEvents(
                hasKeyword ? keyword.trim() : null,
                hasCategory ? category.trim() : null,
                hasCity ? city.trim() : null,
                date
        );
    }

    // 6. GET /api/events/filter - Alias for filter search
    @GetMapping("/filter")
    public List<Event> filterEvents(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return searchEvents(keyword, category, city, date);
    }

    // 7. GET /api/events/category/{category} - Return events matching the category
    @GetMapping("/category/{category}")
    public List<Event> getEventsByCategory(@PathVariable String category) {
        if (category == null || category.trim().isEmpty()) {
            return eventRepository.findAll();
        }
        return eventRepository.findByCategoryIgnoreCase(category.trim());
    }

    // 8. GET /api/events/{id:[0-9]+} - Return one event by ID
    @GetMapping("/{id:[0-9]+}")
    public ResponseEntity<Event> getEventById(@PathVariable Long id) {
        if (id == null) {
            return ResponseEntity.badRequest().build();
        }

        Optional<Event> eventOptional = eventRepository.findById(id);
        return eventOptional
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    // 9. PUT /api/events/{id:[0-9]+} - Update event details
    @PutMapping("/{id:[0-9]+}")
    public ResponseEntity<?> updateEvent(@PathVariable Long id, @RequestBody Event updatedEvent) {
        if (id == null || updatedEvent == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid event update data"));
        }

        Optional<Event> existingOpt = eventRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Event not found with ID: " + id));
        }

        Event existing = existingOpt.get();
        boolean wasCancelled = "CANCELLED".equalsIgnoreCase(existing.getStatus());
        boolean isNowCancelled = "CANCELLED".equalsIgnoreCase(updatedEvent.getStatus());

        if (updatedEvent.getTitle() != null) existing.setTitle(updatedEvent.getTitle());
        if (updatedEvent.getCategory() != null) existing.setCategory(updatedEvent.getCategory());
        if (updatedEvent.getEventDate() != null) existing.setEventDate(updatedEvent.getEventDate());
        if (updatedEvent.getStartTime() != null) existing.setStartTime(updatedEvent.getStartTime());
        if (updatedEvent.getEndTime() != null) existing.setEndTime(updatedEvent.getEndTime());
        if (updatedEvent.getVenue() != null) existing.setVenue(updatedEvent.getVenue());
        if (updatedEvent.getCity() != null) existing.setCity(updatedEvent.getCity());
        if (updatedEvent.getCollege() != null) existing.setCollege(updatedEvent.getCollege());
        if (updatedEvent.getOrganizerName() != null) existing.setOrganizerName(updatedEvent.getOrganizerName());
        if (updatedEvent.getOrganizerContact() != null) existing.setOrganizerContact(updatedEvent.getOrganizerContact());
        if (updatedEvent.getDescription() != null) existing.setDescription(updatedEvent.getDescription());
        if (updatedEvent.getEligibility() != null) existing.setEligibility(updatedEvent.getEligibility());
        if (updatedEvent.getMaxParticipants() != null) existing.setMaxParticipants(updatedEvent.getMaxParticipants());
        if (updatedEvent.getRegistrationDeadline() != null) existing.setRegistrationDeadline(updatedEvent.getRegistrationDeadline());
        if (updatedEvent.getFlyerUrl() != null) existing.setFlyerUrl(updatedEvent.getFlyerUrl());
        if (updatedEvent.getStatus() != null) existing.setStatus(updatedEvent.getStatus().toUpperCase());

        Event saved = eventRepository.save(existing);

        try {
            if (!wasCancelled && isNowCancelled) {
                notificationService.notifyEventAttendees(
                        id,
                        "Event Cancelled",
                        saved.getTitle() + " has been cancelled.",
                        "CANCELLED"
                );
            } else {
                notificationService.notifyEventAttendees(
                        id,
                        "Event Updated",
                        "Details for " + saved.getTitle() + " have been updated.",
                        "UPDATE"
                );
            }
        } catch (Exception e) {
            System.err.println("Failed to broadcast event notification: " + e.getMessage());
        }

        return ResponseEntity.ok(saved);
    }

    // 10. PUT /api/events/{id:[0-9]+}/cancel - Cancel an event directly
    @PutMapping("/{id:[0-9]+}/cancel")
    public ResponseEntity<?> cancelEvent(@PathVariable Long id) {
        if (id == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Event ID is required"));
        }

        Optional<Event> existingOpt = eventRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Event not found with ID: " + id));
        }

        Event existing = existingOpt.get();
        existing.setStatus("CANCELLED");
        Event saved = eventRepository.save(existing);

        try {
            notificationService.notifyEventAttendees(
                    id,
                    "Event Cancelled",
                    saved.getTitle() + " has been cancelled.",
                    "CANCELLED"
            );
        } catch (Exception e) {
            System.err.println("Failed to broadcast cancel notification: " + e.getMessage());
        }

        return ResponseEntity.ok(Map.of("message", "Event cancelled successfully", "event", saved));
    }

    // 11. DELETE /api/events/{id:[0-9]+} - Delete event and associated registrations
    @DeleteMapping("/{id:[0-9]+}")
    public ResponseEntity<?> deleteEvent(@PathVariable Long id) {
        if (id == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Event ID is required"));
        }

        Optional<Event> eventOpt = eventRepository.findById(id);
        if (eventOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Event not found with ID: " + id));
        }

        // Clean up registrations for this event
        List<EventRegistration> registrations = eventRegistrationRepository.findByEventId(id);
        if (!registrations.isEmpty()) {
            eventRegistrationRepository.deleteAll(registrations);
        }

        eventRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Event deleted successfully"));
    }
}
