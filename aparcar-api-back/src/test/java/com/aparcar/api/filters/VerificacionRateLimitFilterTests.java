package com.aparcar.api.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Caja blanca del limite de subidas de certificados, con el filtro suelto. */
@ExtendWith(MockitoExtension.class)
class VerificacionRateLimitFilterTests {
    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private VerificacionRateLimitFilter filter;
    private StringWriter cuerpo;

    @BeforeEach
    void setUp() throws Exception {
        filter = new VerificacionRateLimitFilter(5);
        cuerpo = new StringWriter();
        lenient().when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        lenient().when(request.getContextPath()).thenReturn("");
        lenient().when(request.getMethod()).thenReturn("POST");
        lenient().when(request.getRequestURI()).thenReturn("/api/v1/verificaciones-discapacidad");
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(cuerpo));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static void autenticar(String usuario) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                usuario, null, List.of(new SimpleGrantedAuthority("USER"))));
    }

    @Test
    @DisplayName("solo cuenta el POST de la subida: ver el estado o el documento no consume cupo")
    void soloLaSubida() {
        assertFalse(filter.shouldNotFilter(request));

        when(request.getMethod()).thenReturn("GET");
        assertTrue(filter.shouldNotFilter(request));
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/verificaciones-discapacidad/abc/aprobar",
                "/api/v1/verificaciones-discapacidad/mia/cancelar");
        assertTrue(filter.shouldNotFilter(request));
        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    @DisplayName("deja pasar 5 subidas por hora y la sexta recibe 429 en el formato del proyecto")
    void cincoPorHora() throws Exception {
        autenticar("juan@test.com");
        for (int i = 0; i < 6; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        verify(filterChain, times(5)).doFilter(request, response);
        verify(response).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        assertTrue(cuerpo.toString().contains("\"code\":429"));
    }

    @Test
    @DisplayName("cuenta por cuenta: otra persona desde la misma IP tiene su propio cupo")
    void porCuenta() throws Exception {
        autenticar("juan@test.com");
        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }
        autenticar("eva@test.com");
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(6)).doFilter(request, response);
        verify(response, never()).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    @DisplayName("no choca con los otros limitadores: cada clase marca la request con su propio atributo")
    void noChocaConLosOtros() throws Exception {
        Method nombre = OncePerRequestFilter.class.getDeclaredMethod("getAlreadyFilteredAttributeName");
        nombre.setAccessible(true);

        assertEquals(VerificacionRateLimitFilter.class.getName() + ".FILTERED", nombre.invoke(filter));
        assertFalse(nombre.invoke(filter).equals(nombre.invoke(new RateLimitFilter())));
        assertFalse(nombre.invoke(filter).equals(nombre.invoke(new AccesoRateLimitFilter(60))));
    }
}
