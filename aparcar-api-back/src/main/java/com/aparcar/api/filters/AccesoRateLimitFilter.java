package com.aparcar.api.filters;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Rate limit de la validacion de codigos de acceso (/api/v1/accesos/**).
 *
 * <p>Mismo patron que {@link RateLimitFilter} (Bucket4j + Caffeine), con otro
 * limite: aquel es de 5 por minuto, pensado para login; en la puerta un
 * operador valida autos seguidos, asi que el limite es operativo (60 por minuto
 * por defecto) y alcanza para frenar a quien pruebe codigos al azar.
 *
 * <p>Es una clase aparte y no otra instancia de {@link RateLimitFilter}:
 * {@link OncePerRequestFilter} marca cada request con un atributo derivado del
 * nombre de la clase, asi que dos filtros de la misma clase en la cadena se
 * pisarian y el segundo nunca correria.
 *
 * <p>Va despues de la validacion del JWT y la cuenta es por operador: en un
 * predio varios operadores suelen salir por la misma IP. Sin usuario
 * autenticado se cuenta por IP.
 */
public class AccesoRateLimitFilter extends OncePerRequestFilter {
    private static final String RUTA = "/api/v1/accesos/";

    private final int porMinuto;
    private final LoadingCache<String, Bucket> cache;

    public AccesoRateLimitFilter(int porMinuto) {
        this.porMinuto = porMinuto;
        this.cache = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(10))
                .maximumSize(10_000)
                .build(key -> newBucket());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        Bucket bucket = cache.get(clave(request));

        if (bucket != null && bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"code\":429,\"message\":\"Demasiadas validaciones seguidas. "
                    + "Esperá un momento y volvé a intentar.\",\"details\":null}");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith(RUTA);
    }

    private String clave(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getName() != null) {
            return "usuario:" + authentication.getName();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit
                        .capacity(porMinuto)
                        .refillGreedy(porMinuto, Duration.ofMinutes(1)))
                .build();
    }
}
