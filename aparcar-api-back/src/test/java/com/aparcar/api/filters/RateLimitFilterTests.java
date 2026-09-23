package com.aparcar.api.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.PrintWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caja blanca: instancia el filtro de rate limiting directamente (sin
 * contexto de Spring) para poder agotar el bucket a propósito sin
 * contaminar el resto de la suite de integración, que comparte el mismo
 * bucket por IP en /login, /register y /forgot-password.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTests {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        filter = new RateLimitFilter();
        lenient().when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(java.io.Writer.nullWriter()));
    }

    @Test
    @DisplayName("shouldNotFilter deja pasar rutas que no son login/register/forgot-password")
    void shouldNotFilterSkipsUnrelatedPaths() {
        when(request.getServletPath()).thenReturn("/api/v1/reservas");
        assertEquals(true, filter.shouldNotFilter(request));
    }

    @Test
    @DisplayName("shouldNotFilter aplica rate limit a login, register y forgot-password")
    void shouldFilterAppliesToSensitiveEndpoints() {
        when(request.getServletPath()).thenReturn("/login", "/register", "/forgot-password");
        assertEquals(false, filter.shouldNotFilter(request));
        assertEquals(false, filter.shouldNotFilter(request));
        assertEquals(false, filter.shouldNotFilter(request));
    }

    @Test
    @DisplayName("permite las primeras 5 requests de una misma IP")
    void allowsFirstFiveRequestsFromSameIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        verify(filterChain, times(5)).doFilter(request, response);
        verify(response, org.mockito.Mockito.never()).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    @DisplayName("la 6ta request de la misma IP en la ventana recibe 429 y no llega al resto de la cadena")
    void rejectsSixthRequestWithTooManyRequests() throws Exception {
        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        // La cadena se llamó 5 veces (las permitidas), nunca una 6ta.
        verify(filterChain, times(5)).doFilter(request, response);
    }

    @Test
    @DisplayName("dos IPs distintas tienen buckets independientes")
    void differentIpsHaveIndependentBuckets() throws Exception {
        // Agota el bucket de la primera IP (5 permitidas + 1 rechazada).
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        for (int i = 0; i < 6; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }
        verify(response, times(1)).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());

        // La segunda IP todavía tiene su bucket lleno.
        when(request.getRemoteAddr()).thenReturn("10.0.0.2");
        filter.doFilterInternal(request, response, filterChain);
        verify(response, times(1)).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        verify(filterChain, times(6)).doFilter(request, response);
    }
}
