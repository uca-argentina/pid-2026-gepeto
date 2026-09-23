package com.aparcar.api.controller;

import com.aparcar.api.dto.auth.RegisteredUserDto;
import com.aparcar.api.dto.auth.RegistrationDto;
import com.aparcar.api.dto.auth.ResetPasswordDto;
import com.aparcar.api.dto.auth.UserEmailDto;
import com.aparcar.api.service.IAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

@RestController
@RequiredArgsConstructor
public class AuthController {
    private final IAuthService authService;

    @PostMapping("/register")
    public ResponseEntity<RegisteredUserDto> register(@Valid @RequestBody RegistrationDto registrationDto) {
        // Son datos JSON, no HTML. Escaparlos aquí alteraría el email de login.
        RegisteredUserDto registeredUser = authService.register(registrationDto);

        return ResponseEntity.status(HttpStatus.CREATED).body(registeredUser);
    }

    /**
     * Performs the logic additional to the authentication process.
     * If the user is authenticated successfully in the middleware,
     * this method will be called.
     *
     * @return a ResponseEntity with HTTP status OK and username
     */
    @PostMapping("/login")
    public ResponseEntity<String> login() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        return ResponseEntity.ok(username);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody UserEmailDto dto) {
        dto.setEmail(HtmlUtils.htmlEscape(dto.getEmail()));
        authService.createAndSendOTP(dto.getEmail());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordDto dto) {
        authService.resetPassword(dto.getEmail(), dto.getOtp(), dto.getNewPassword());
        return ResponseEntity.ok().build();
    }
}
