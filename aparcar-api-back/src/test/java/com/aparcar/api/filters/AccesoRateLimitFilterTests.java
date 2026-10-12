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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caja blanca del rate limit de /api/v1/accesos/**. Se instancia el filtro
 * suelto, como en {@code RateLimitFilterTests}, para agotar el bucket sin
 * contaminar la suite de integracion (que usa un limite alto a proposito).
 */
@ExtendWith(MockitoExtension.class)
class AccesoRateLimitFilterTests {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private AccesoRateLimitFilter filter;
    private StringWriter cuerpo;

    @BeforeEach
    void setUp() throws Exception {
        filter = new AccesoRateLimitFilter(60);
        cuerpo = new StringWriter();
        lenient().when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        lenient().when(request.getContextPath()).thenReturn("");
        lenient().when(request.getRequestURI()).thenReturn("/api/v1/accesos/consulta");
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(cuerpo));
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private static void autenticar(String usuario) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                usuario, null, List.of(new SimpleGrantedAuthority("ADMIN"))));
    }

    @Test
    @DisplayName("solo filtra /api/v1/accesos/**")
    void soloFiltraAccesos() {
        assertFalse(filter.shouldNotFilter(request));

        when(request.getRequestURI()).thenReturn("/api/v1/reservas", "/login", "/api/v1/accesosx");
        assertTrue(filter.shouldNotFilter(request));
        assertTrue(filter.shouldNotFilter(request));
        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    @DisplayName("deja pasar 60 validaciones por minuto: un limite operativo, no el de 5 del login")
    void permiteSesentaPorMinuto() throws Exception {
        autenticar("operador@test.com");
        for (int i = 0; i < 60; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        verify(filterChain, times(60)).doFilter(request, response);
        verify(response, never()).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    @DisplayName("la 61ra recibe 429 con un mensaje en el formato de errores del proyecto")
    void rechazaLaSiguienteCon429() throws Exception {
        autenticar("operador@test.com");
        for (int i = 0; i < 61; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        verify(response).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        verify(filterChain, times(60)).doFilter(request, response);
        assertTrue(cuerpo.toString().contains("\"code\":429"), cuerpo.toString());
    }

    // En un predio varios operadores suelen salir por la misma IP.
    @Test
    @DisplayName("cuenta por operador: otro operador desde la misma IP tiene su propio cupo")
    void cuentaPorOperador() throws Exception {
        autenticar("uno@test.com");
        for (int i = 0; i < 61; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }
        verify(response, times(1)).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());

        autenticar("otro@test.com");
        filter.doFilterInternal(request, response, filterChain);

        verify(response, times(1)).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        verify(filterChain, times(61)).doFilter(request, response);
    }

    @Test
    @DisplayName("sin usuario autenticado cuenta por IP")
    void sinUsuarioCuentaPorIp() throws Exception {
        for (int i = 0; i < 61; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }
        verify(response, times(1)).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());

        when(request.getRemoteAddr()).thenReturn("10.0.0.2");
        filter.doFilterInternal(request, response, filterChain);
        verify(filterChain, times(61)).doFilter(request, response);
    }

    // OncePerRequestFilter marca la request con "<nombre de clase>.FILTERED":
    // si los dos filtros compartieran clase, el segundo se salteaba solo.
    @Test
    @DisplayName("no choca con RateLimitFilter: cada uno marca la request con su propio atributo")
    void noChocaConElFiltroDeLogin() throws Exception {
        Method nombre = OncePerRequestFilter.class.getDeclaredMethod("getAlreadyFilteredAttributeName");
        nombre.setAccessible(true);

        assertNotEquals(nombre.invoke(new RateLimitFilter()), nombre.invoke(filter));
        assertEquals(AccesoRateLimitFilter.class.getName() + ".FILTERED", nombre.invoke(filter));
    }
}
