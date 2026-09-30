package com.eventra.backend.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseMigrationConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationConfig.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigrationConfig(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrate() {
        try {
            // Allow phone to be nullable in users table so email-only users can register/login
            jdbcTemplate.execute("ALTER TABLE users MODIFY COLUMN phone VARCHAR(255) NULL");
            log.info("[DB Migration] users.phone column set to nullable");
        } catch (Exception e) {
            log.debug("[DB Migration] users.phone modify skipped/already nullable: {}", e.getMessage());
        }

        try {
            // Clean up empty string emails to NULL so unique constraints work correctly
            jdbcTemplate.execute("UPDATE users SET email = NULL WHERE email = ''");
        } catch (Exception e) {
            log.debug("[DB Migration] users email cleanup skipped: {}", e.getMessage());
        }

        try {
            // Allow phone to be nullable in otp_verifications table
            jdbcTemplate.execute("ALTER TABLE otp_verifications MODIFY COLUMN phone VARCHAR(255) NULL");
            log.info("[DB Migration] otp_verifications.phone column set to nullable");
        } catch (Exception e) {
            log.debug("[DB Migration] otp_verifications.phone modify skipped/already nullable: {}", e.getMessage());
        }

        try {
            // Add email column to otp_verifications if not present
            jdbcTemplate.execute("ALTER TABLE otp_verifications ADD COLUMN email VARCHAR(255) NULL");
            log.info("[DB Migration] otp_verifications.email column added");
        } catch (Exception e) {
            log.debug("[DB Migration] otp_verifications.email column already exists: {}", e.getMessage());
        }
    }
}
