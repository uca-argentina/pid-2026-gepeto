package com.aparcar.api.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Custom authentication entry point that returns JSON error responses for
 * unauthorized requests.
 * Logs unauthorized access attempts with client IP and timestamp for security
 * monitoring.
 */
@Slf4j
public class CustomBasicAuthenticationEntryPoint implements org.springframework.security.web.AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        LocalDateTime timestamp = LocalDateTime.now();
        String message = "Unauthorized";
        String path = request.getRequestURI();
        String clientIp = request.getRemoteAddr();
        response.setHeader("error-reason", message);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");

        log.warn("Denied access to {} from IP {} at {}.", path, clientIp, timestamp);

        String jsonResponse = String.format("{\"timestamp\": \"%s\", \"status\": %d, \"error\": \"%s\", \"message\": \"%s\", \"path\": \"%s\", \"client_ip\": \"%s\"}",
                timestamp, HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase(), message, path, clientIp);
        response.getWriter().write(jsonResponse);
    }
}
