package com.aparcar.api.service;

import com.aparcar.api.component.IEmailSender;
import com.aparcar.api.config.UnitTests;
import com.aparcar.api.dto.auth.RegisteredUserDto;
import com.aparcar.api.dto.auth.RegistrationDto;
import com.aparcar.api.dto.email.PlainEmailData;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.auth.OneTimePassword;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.repository.VisitanteRepository;
import com.aparcar.api.repository.OneTimePasswordRepository;
import com.aparcar.api.service.impl.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@UnitTests
public class AuthServiceTests {

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private VisitanteRepository visitanteRepository;

    @Mock
    private OneTimePasswordRepository otpRepository;

    @Mock
    private IEmailSender emailSender;

    @InjectMocks
    private AuthService authService;

    private final String existingEmail = "some@email.com";
    private final String notFoundEmail = "notfound@email.com";

    @Test
    void concurrentEmailConflictReturnsValidationError() {
        when(visitanteRepository.existsByEmail(existingEmail)).thenReturn(false, true);
        when(visitanteRepository.save(any(Visitante.class)))
                .thenThrow(new DataIntegrityViolationException("unique email"));
        ValidationException error = assertThrows(ValidationException.class, () ->
                authService.register(new RegistrationDto("Test", "30111222", existingEmail, "password123", null)));
        assertEquals("Ya existe una cuenta asociada a ese email.", error.getMessage());
    }

    @Test
    void concurrentDocumentConflictReturnsValidationError() {
        when(visitanteRepository.existsByDocumento("30111222")).thenReturn(false, true);
        when(visitanteRepository.save(any(Visitante.class)))
                .thenThrow(new DataIntegrityViolationException("unique document"));
        ValidationException error = assertThrows(ValidationException.class, () ->
                authService.register(new RegistrationDto("Test", "30111222", existingEmail, "password123", null)));
        assertEquals("Ya existe un visitante con ese documento.", error.getMessage());
    }

    @Test
    @DisplayName("register throws ValidationException when email already registered")
    void registerThrowsValidationExceptionWhenEmailAlreadyRegistered() {
        // Arrange
        when(visitanteRepository.existsByEmail(existingEmail)).thenReturn(true);

        // Act & Assert
        assertThrows(ValidationException.class, () ->
                authService.register(new RegistrationDto("Test User", "30111222", existingEmail, "password123", null)));
    }

    // El documento identifica al visitante y toda cuenta es un visitante, asi
    // que el alta administrativa tambien tiene que respetar que sea unico.
    @Test
    @DisplayName("register throws ValidationException when documento already registered")
    void registerThrowsValidationExceptionWhenDocumentoAlreadyRegistered() {
        when(visitanteRepository.existsByEmail(notFoundEmail)).thenReturn(false);
        when(visitanteRepository.existsByDocumento("30111222")).thenReturn(true);

        assertThrows(ValidationException.class, () ->
                authService.register(new RegistrationDto("Test User", "30111222", notFoundEmail, "password123", null)));
    }

    @Test
    @DisplayName("register successfully creates a new user")
    void registerSuccessfullyCreatesNewUser() {
        // Arrange
        when(visitanteRepository.existsByEmail(notFoundEmail)).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashedPassword");
        when(visitanteRepository.save(any(Visitante.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        RegisteredUserDto result = authService.register(
                new RegistrationDto("Test User", "30111222", notFoundEmail, "password123", null));

        // Assert
        assertEquals(result.email(), notFoundEmail);
        assertEquals("30111222", result.documento());
        assertEquals(1, result.authorities().size());
        assertEquals("USER", result.authorities().iterator().next().name());

        ArgumentCaptor<Visitante> captor = ArgumentCaptor.forClass(Visitante.class);
        verify(visitanteRepository).save(captor.capture());
        Visitante savedUser = captor.getValue();
        assertEquals(notFoundEmail, savedUser.getEmail());
        assertEquals("30111222", savedUser.getDocumento());
        assertEquals("hashedPassword", savedUser.getPassword());
        assertEquals(1, savedUser.getAuthorities().size());
        assertEquals("USER", savedUser.getAuthorities().iterator().next().name());
        // Nace activa: la crea un admin desde el panel, no alguien registrandose
        // solo, asi que no hay nada que aprobar despues.
        assertEquals(true, savedUser.getIsActive());
    }

    @Test
    @DisplayName("createAndSendOTP throws NotFoundException when user not found")
    void createAndSendOTPThrowsNotFoundExceptionWhenUserNotFound() {
        // Arrange
        when(visitanteRepository.findByEmail(notFoundEmail)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(NotFoundException.class, () -> authService.createAndSendOTP(notFoundEmail));
    }

    @Test
    @DisplayName("createAndSendOTP successfully creates and sends OTP")
    void createAndSendOTPSuccessfullyCreatesAndSendsOTP() {
        // Arrange
        Visitante user = new Visitante();
        user.setEmail(existingEmail);
        when(visitanteRepository.findByEmail(existingEmail)).thenReturn(Optional.of(user));
        when(otpRepository.findByUser(any(Visitante.class))).thenReturn(Optional.of(new OneTimePassword()));
        doNothing().when(otpRepository).delete(any(OneTimePassword.class));
        when(otpRepository.save(any(OneTimePassword.class))).thenAnswer(i -> i.getArguments()[0]);
        doNothing().when(emailSender).sendPlainTextEmail(any(PlainEmailData.class));

        // Act
        authService.createAndSendOTP(existingEmail);

        // Assert
        verify(otpRepository, times(1)).delete(any(OneTimePassword.class));

        ArgumentCaptor<OneTimePassword> otpCaptor = ArgumentCaptor.forClass(OneTimePassword.class);
        verify(otpRepository, times(1)).save(otpCaptor.capture());
        String otp = otpCaptor.getValue().getToken();
        assertEquals(6, otp.length());

        ArgumentCaptor<PlainEmailData> emailCaptor = ArgumentCaptor.forClass(PlainEmailData.class);
        verify(emailSender, times(1)).sendPlainTextEmail(emailCaptor.capture());
        PlainEmailData emailData = emailCaptor.getValue();
        assertTrue(emailData.getBody().contains(otp));
        assertEquals(existingEmail, emailData.getRecipients().getFirst());
    }

    @Test
    @DisplayName("resetPassword throws NotFoundException when user not found")
    void resetPasswordThrowsNotFoundExceptionWhenUserNotFound() {
        // Arrange
        when(visitanteRepository.findByEmail(notFoundEmail)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(NotFoundException.class, () ->
                authService.resetPassword(notFoundEmail, "someToken", "newPassword123"));
    }

    @Test
    @DisplayName("resetPassword throws OTPException when OTP is invalid")
    void resetPasswordThrowsOTPExceptionWhenOTPIsInvalid() {
        // Arrange
        when(visitanteRepository.findByEmail(existingEmail)).thenReturn(Optional.of(new Visitante()));
        when(otpRepository.findByUserAndToken(any(Visitante.class), any(String.class)))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ValidationException.class, () ->
                authService.resetPassword(existingEmail, "invalidToken", "newPassword123"));
    }

    @Test
    @DisplayName("resetPassword throws OTPException when OTP is expired")
    void resetPasswordThrowsOTPExceptionWhenOTPIsExpired() {
        // Arrange
        when(visitanteRepository.findByEmail(existingEmail)).thenReturn(Optional.of(new Visitante()));
        when(otpRepository.findByUserAndToken(any(Visitante.class), any(String.class)))
                .thenReturn(Optional.of(new OneTimePassword(
                        null, "expiredToken", Instant.now().minusSeconds(60))));

        // Act & Assert
        assertThrows(ValidationException.class, () ->
                authService.resetPassword(existingEmail, "invalidToken", "newPassword123"));
    }

    @Test
    @DisplayName("resetPassword successfully resets the user's password")
    void resetPasswordSuccessfullyResetsUserPassword() {
        // Arrange
        when(visitanteRepository.findByEmail(existingEmail)).thenReturn(Optional.of(new Visitante()));
        when(otpRepository.findByUserAndToken(any(Visitante.class), any(String.class)))
                .thenReturn(Optional.of(new OneTimePassword(
                        null, "valid token", Instant.now().plusSeconds(60))));
        when(otpRepository.save(any(OneTimePassword.class))).thenAnswer(i -> i.getArguments()[0]);
        when(passwordEncoder.encode("newPassword123")).thenReturn("hashedNewPassword");
        when(visitanteRepository.save(any(Visitante.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        authService.resetPassword(existingEmail, "valid token", "newPassword123");

        // Assert
        ArgumentCaptor<Visitante> captor = ArgumentCaptor.forClass(Visitante.class);
        verify(visitanteRepository, times(1)).save(captor.capture());
        Visitante updatedUser = captor.getValue();
        assertEquals("hashedNewPassword", updatedUser.getPassword());
    }
}
