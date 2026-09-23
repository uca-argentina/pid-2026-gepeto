package com.aparcar.api.component;

import com.aparcar.api.config.UnitTests;
import com.aparcar.api.repository.OneTimePasswordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@UnitTests
public class OTPCleanupTests {

    @Mock
    private OneTimePasswordRepository otpRepository;

    @InjectMocks
    private OTPCleanup otpCleanup;

    @Test
    @DisplayName("otpCleanup deletes all expired OTPs")
    void otpCleanupDeletesAllExpiredOTPs() {
        // Arrange
        when(otpRepository.deleteExpired(any(Instant.class))).thenReturn(1);

        // Act & Assert
        assertDoesNotThrow(() -> otpCleanup.cleanupExpiredOTPs());

        // Assert
        verify(otpRepository).deleteExpired(any(Instant.class));
    }
}
