package com.aparcar.api.component;

import com.aparcar.api.component.impl.RevokedUserCache;
import com.aparcar.api.config.UnitTests;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@UnitTests
public class RevokedUserCacheTests {

    @Test
    @DisplayName("RevokedUserCache revokes and checks revoked users")
    void testRevoke() {
        // Arrange
        var revokedUserCache = new RevokedUserCache();
        String email = "someemail@mail.com";

        // Act
        revokedUserCache.revoke(email);

        // Assert
        assertTrue(revokedUserCache.isRevoked(email));
        assertFalse(revokedUserCache.isRevoked("someotheremai@gmail.com"));
    }

    @Test
    @DisplayName("RevokedUserCache rejects nulls")
    void cacheRejectsNulls() {
        // Arrange
        var revokedUserCache = new RevokedUserCache();

        // Act & Assert
        assertTrue(revokedUserCache.isRevoked(null));
        assertTrue(revokedUserCache.isRevoked("null"));
    }
}
