package com.aparcar.api.component;

/**
 * Interface for managing revoked user tokens.
 * Used to invalidate JWTs before their natural expiration.
 */
public interface IRevokedUserCache {
    /**
     * Marks a user's token as revoked.
     *
     * @param email The user's email address.
     */
    void revoke(String email);

    /**
     * Checks if a user's token has been revoked.
     *
     * @param email The user's email address.
     * @return true if the token is revoked, false otherwise.
     */
    boolean isRevoked(String email);

    /**
     * Clears all revoked tokens from the cache.
     */
    void clear();
}
