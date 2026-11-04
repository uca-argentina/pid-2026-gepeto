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
 * Limita la subida de certificados de discapacidad: pocas por hora y por
 * cuenta ({@code aparcar.discapacidad.subidas-por-hora}, 5 por defecto).
 *
 * <p>Solo cuenta el POST de la subida; consultar el estado o ver el propio
 * certificado no consume cupo. Mismo patron que {@link RateLimitFilter} y
 * {@link AccesoRateLimitFilter}, pero en una clase propia: {@link OncePerRequestFilter}
 * identifica cada filtro por su nombre de clase, asi que reusar la de otro
 * haria que uno de los dos no corriera.
 *
 * <p>Va despues de la validacion del JWT para contar por cuenta; sin usuario
 * autenticado se cuenta por IP (y la seguridad igual responde 401).
 */
public class VerificacionRateLimitFilter extends OncePerRequestFilter {
    private static final String RUTA = "/api/v1/verificaciones-discapacidad";

    private final int porHora;
    private final LoadingCache<String, Bucket> cache;

    public VerificacionRateLimitFilter(int porHora) {
        this.porHora = porHora;
        this.cache = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofHours(2))
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
            response.getWriter().write("{\"code\":429,\"message\":\"Subiste demasiados certificados seguidos. "
                    + "Esperá un rato y volvé a intentar.\",\"details\":null}");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !("POST".equalsIgnoreCase(request.getMethod()) && (RUTA.equals(path) || (RUTA + "/").equals(path)));
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
                        .capacity(porHora)
                        .refillGreedy(porHora, Duration.ofHours(1)))
                .build();
    }
}
