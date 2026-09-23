package com.aparcar.api.filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aparcar.api.component.IRevokedUserCache;
import com.aparcar.api.dto.ErrorResponseDto;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.aparcar.api.config.ApplicationConstants.*;

/**
 * Filter responsible for validating the JWT token in the {@code Authorization}
 * header.
 * If the token is valid, it populates the {@link SecurityContextHolder} with
 * the user details.
 */
@Slf4j
@RequiredArgsConstructor
public class JWTValidationFilter extends OncePerRequestFilter {
    private final IRevokedUserCache revokedCache;
    private final String secret;
    private final boolean isDev;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String jwt = request.getHeader(JWT_HEADER);
        if (jwt != null && jwt.startsWith("Bearer ")) {
            jwt = jwt.substring(7); // Removes "Bearer "

            try {
                SecretKey secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

                Claims claims = Jwts.parser().verifyWith(secretKey).build()
                        .parseSignedClaims(jwt).getPayload();
                String email = String.valueOf(claims.get("email"));
                if (revokedCache.isRevoked(email)) {
                    log.error("Request to parse expired JWT : {} failed : Revoked token", jwt);
                    sendUnauthorized(request, response, "Revoked token");
                    return;
                }
                String authorities = String.valueOf(claims.get("authorities"));

                Authentication authentication = new UsernamePasswordAuthenticationToken(email, null,
                        AuthorityUtils.commaSeparatedStringToAuthorityList(authorities));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ExpiredJwtException e) {
                log.error("Request to parse expired JWT : {} failed : {}", jwt, e.getMessage());
                sendUnauthorized(request, response, e.getMessage());
                return;
            } catch (Exception e) {
                log.error("Error occurred while parsing JWT : {}", e.getMessage());
                sendUnauthorized(request, response, e.getMessage());
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().equals("/login")
                || request.getServletPath().equals("/register");
    }

    private void sendUnauthorized(HttpServletRequest request, HttpServletResponse response, String exceptionMessage)
            throws IOException {
        String msg = isDev
                ? "Invalid credentials: " + exceptionMessage
                : "Unauthorized.";

        ErrorResponseDto dto = new ErrorResponseDto(
                HttpStatus.UNAUTHORIZED.value(),
                msg,
                null);

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), dto);
    }
}
