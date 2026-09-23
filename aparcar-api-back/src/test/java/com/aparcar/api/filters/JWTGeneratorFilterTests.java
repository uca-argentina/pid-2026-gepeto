package com.aparcar.api.filters;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caja blanca: prueba el filtro que genera el JWT directamente,
 * sin levantar Spring, mockeando el request/response/chain.
 */
@ExtendWith(MockitoExtension.class)
class JWTGeneratorFilterTests {

    private static final String SECRET = "test-secret-key-for-jwt-generation-1234567890";

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JWTGeneratorFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JWTGeneratorFilter(SECRET);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("shouldNotFilter devuelve false solo para /login")
    void shouldNotFilterOnlyAppliesToLogin() {
        when(request.getServletPath()).thenReturn("/login");
        assertEquals(false, filter.shouldNotFilter(request));

        when(request.getServletPath()).thenReturn("/api/v1/reservas");
        assertEquals(true, filter.shouldNotFilter(request));
    }

    @Test
    @DisplayName("doFilterInternal no agrega header Authorization si no hay autenticación")
    void doesNotSetHeaderWhenNoAuthentication() throws Exception {
        filter.doFilterInternal(request, response, filterChain);

        verify(response, org.mockito.Mockito.never()).setHeader(org.mockito.ArgumentMatchers.eq("Authorization"), org.mockito.ArgumentMatchers.anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("doFilterInternal genera un JWT con email y authorities cuando hay autenticación")
    void generatesJwtWithEmailAndAuthoritiesWhenAuthenticated() throws Exception {
        var authorities = List.of(new SimpleGrantedAuthority("USER"), new SimpleGrantedAuthority("ADMIN"));
        var authentication = new UsernamePasswordAuthenticationToken("mateo@mateo.com", "irrelevant", authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filter.doFilterInternal(request, response, filterChain);

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(org.mockito.ArgumentMatchers.eq("Authorization"), captor.capture());
        verify(filterChain).doFilter(request, response);

        String headerValue = captor.getValue();
        assertEquals(true, headerValue.startsWith("Bearer "));

        String jwt = headerValue.substring(7);
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(jwt).getPayload();

        assertEquals("mateo@mateo.com", claims.get("email"));
        assertEquals("AparcAR", claims.getIssuer());
        // El orden de las authorities no está garantizado, así que separamos y comparamos como set.
        String[] claimedAuthorities = String.valueOf(claims.get("authorities")).split(",");
        assertEquals(2, claimedAuthorities.length);
    }

    @Test
    @DisplayName("El JWT expira 8 horas después de emitido")
    void jwtExpiresEightHoursAfterIssued() throws Exception {
        var authentication = new UsernamePasswordAuthenticationToken(
                "mateo@mateo.com", "irrelevant", List.of(new SimpleGrantedAuthority("USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filter.doFilterInternal(request, response, filterChain);

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(org.mockito.ArgumentMatchers.eq("Authorization"), captor.capture());

        String jwt = captor.getValue().substring(7);
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(jwt).getPayload();

        long diffMillis = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertEquals(8 * 60 * 60 * 1000, diffMillis);
    }
}
