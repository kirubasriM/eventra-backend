package com.eventra.backend.repository;

import com.eventra.backend.entity.EventRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventRegistrationRepository
        extends JpaRepository<EventRegistration, Long> {

    List<EventRegistration> findByUserId(Long userId);

    List<EventRegistration> findByEventId(Long eventId);

    Optional<EventRegistration> findByRegistrationId(String registrationId);

    boolean existsByEventIdAndUserId(Long eventId, Long userId);
}