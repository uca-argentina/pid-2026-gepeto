package com.aparcar.api.service;

import com.aparcar.api.dto.auth.RegisteredUserDto;
import com.aparcar.api.dto.auth.RegistrationDto;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.ValidationException;

/**
 * Interface defining the contract for authentication and identity-related
 * services.
 */
public interface IAuthService {
    /**
     * Registers a new user in the system.
     *
     * @param registrationDto The user registration data.
     * @return The data of the successfully registered user.
     * @throws ValidationException If registration fails (e.g., email already
     *                             exists).
     */
    RegisteredUserDto register(RegistrationDto registrationDto);

    /**
     * Creates and sends a One-Time Password (OTP) to the specified email for
     * password recovery.
     *
     * @param email The user's email address.
     * @throws NotFoundException If the user is not found.
     */
    void createAndSendOTP(String email);

    /**
     * Resets the user's password using a valid OTP token.
     *
     * @param email       The user's email address.
     * @param token       The OTP token received by email.
     * @param newPassword The new password to set.
     * @throws ValidationException If the token is invalid or expired.
     */
    void resetPassword(String email, String token, String newPassword);
}
