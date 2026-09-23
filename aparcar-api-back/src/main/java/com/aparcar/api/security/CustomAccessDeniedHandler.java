package com.aparcar.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Handler custom para AccessDeniedException (403), para que la respuesta
 * tenga el mismo formato JSON que CustomBasicAuthenticationEntryPoint (401).
 */
@Slf4j
public class CustomAccessDeniedHandler implements AccessDeniedHandler {
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        LocalDateTime timestamp = LocalDateTime.now();
        String message = accessDeniedException.getMessage() != null
                ? accessDeniedException.getMessage()
                : "Forbidden";
        String path = request.getRequestURI();
        String clientIp = request.getRemoteAddr();

        response.setHeader("error-reason", "Forbidden");
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json;charset=UTF-8");

        log.warn("Forbidden access to {} from IP {} at {}: {}", path, clientIp, timestamp, message);

        String jsonResponse = String.format(
                "{\"timestamp\": \"%s\", \"status\": %d, \"error\": \"%s\", \"message\": \"%s\", \"path\": \"%s\", \"client_ip\": \"%s\"}",
                timestamp, HttpStatus.FORBIDDEN.value(), HttpStatus.FORBIDDEN.getReasonPhrase(), message, path, clientIp);
        response.getWriter().write(jsonResponse);
    }
}