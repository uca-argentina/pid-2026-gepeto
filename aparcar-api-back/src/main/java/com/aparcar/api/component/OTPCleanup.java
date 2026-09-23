package com.aparcar.api.component;

import com.aparcar.api.repository.OneTimePasswordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Scheduled component that periodically removes expired OTP tokens from the
 * database.
 * Runs every hour to maintain database hygiene.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OTPCleanup {
    private final OneTimePasswordRepository otpRepository;

    @Scheduled(cron = "0 0 * * * *") // Every hour
    public void cleanupExpiredOTPs() {
        Instant now = Instant.now();
        log.info("Starting cleanup of expired OTPs (before {})", now);

        long start = System.nanoTime();
        int deleted = otpRepository.deleteExpired(now);
        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;

        log.info("Removed {} OTPs in {} ms", deleted, elapsedMs);
    }
}
