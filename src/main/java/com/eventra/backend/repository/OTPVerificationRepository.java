package com.eventra.backend.repository;

import com.eventra.backend.entity.OTPVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OTPVerificationRepository
        extends JpaRepository<OTPVerification, Long> {

    Optional<OTPVerification> findTopByPhoneOrderByIdDesc(String phone);
}