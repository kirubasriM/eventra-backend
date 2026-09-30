package com.eventra.backend.repository;

import com.eventra.backend.entity.OTPVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OTPVerificationRepository
        extends JpaRepository<OTPVerification, Long> {

    Optional<OTPVerification> findTopByPhoneOrderByIdDesc(String phone);

    long countByPhoneAndCreatedAtAfter(String phone, LocalDateTime afterTime);

    Optional<OTPVerification> findTopByEmailOrderByIdDesc(String email);

    long countByEmailAndCreatedAtAfter(String email, LocalDateTime afterTime);
}
