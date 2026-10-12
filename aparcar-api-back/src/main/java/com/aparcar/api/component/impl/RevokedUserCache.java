package com.aparcar.api.component.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.aparcar.api.component.IRevokedUserCache;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RevokedUserCache implements IRevokedUserCache {
    private final Cache<String, Boolean> revoked = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(8))
            .maximumSize(50)
            .build();

    private final Cache<String, Long> sessionsRevokedBefore = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(8))
            .build();

    @Override
    public void revoke(String email) {
        revoked.put(email, Boolean.TRUE);
    }

    @Override
    public boolean isRevoked(String email) {
        return email == null
                || "null".equals(email)
                || Boolean.TRUE.equals(revoked.getIfPresent(email));
    }

    @Override
    public void revokeSessions(String email) {
        sessionsRevokedBefore.put(email, System.currentTimeMillis());
    }

    @Override
    public boolean isRevoked(String email, long issuedAtMillis) {
        if (isRevoked(email)) return true;
        Long cutoff = sessionsRevokedBefore.getIfPresent(email);
        return cutoff != null && issuedAtMillis <= cutoff;
    }

    @Override
    public void clear() {
        revoked.invalidateAll();
        sessionsRevokedBefore.invalidateAll();
    }
}
