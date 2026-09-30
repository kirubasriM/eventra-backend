package com.eventra.backend.service;

import com.eventra.backend.service.impl.EmailServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailServiceTest {

    @Test
    void testDevelopmentMode_DispatchesSafelyWithoutSending() {
        EmailServiceImpl emailService = new EmailServiceImpl(
                false, "smtp", "", "", "smtp.gmail.com", 587, "no-reply@eventra.in", ""
        );

        assertFalse(emailService.isEmailEnabled());
        boolean sent = emailService.sendOtp("test@example.com", "123456", 5);
        assertTrue(sent, "In development mode, sendOtp should succeed safely");
    }

    @Test
    void testRealEmailMode_MissingCredentialsFailsGracefully() {
        EmailServiceImpl emailService = new EmailServiceImpl(
                true, "smtp", "", "", "smtp.gmail.com", 587, "no-reply@eventra.in", ""
        );

        assertTrue(emailService.isEmailEnabled());
        boolean sent = emailService.sendOtp("test@example.com", "123456", 5);
        assertFalse(sent, "Without SMTP credentials, real email dispatch should fail gracefully");
    }
}
