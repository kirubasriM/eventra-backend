package com.eventra.backend.service;

import com.eventra.backend.entity.OTPVerification;
import com.eventra.backend.repository.OTPVerificationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;

@Service
public class OTPService {

    private final OTPVerificationRepository otpRepository;

    public OTPService(OTPVerificationRepository otpRepository) {
        this.otpRepository = otpRepository;
    }

    public String generateOTP(String phone) {

        // Generate a random 6-digit OTP
        String otp = String.format("%06d",
                new Random().nextInt(1000000));

        // Create OTP record
        OTPVerification verification = new OTPVerification();

        verification.setPhone(phone);
        verification.setOtp(otp);
        verification.setExpiresAt(
                LocalDateTime.now().plusMinutes(5)
        );
        verification.setUsed(false);

        // Save OTP in database
        otpRepository.save(verification);

        return otp;
    }

    public boolean verifyOTP(String phone, String otp) {

        var latestOTP =
                otpRepository.findTopByPhoneOrderByIdDesc(phone);

        // No OTP found
        if (latestOTP.isEmpty()) {
            return false;
        }

        OTPVerification verification = latestOTP.get();

        // OTP already used
        if (verification.isUsed()) {
            return false;
        }

        // OTP expired
        if (LocalDateTime.now().isAfter(
                verification.getExpiresAt())) {
            return false;
        }

        // OTP does not match
        if (!verification.getOtp().equals(otp)) {
            return false;
        }

        // Mark OTP as used
        verification.setUsed(true);
        otpRepository.save(verification);

        return true;
    }
}
