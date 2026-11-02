package com.aparcar.api.security.securityConfig;

import com.aparcar.api.component.IRevokedUserCache;
import com.aparcar.api.config.AccesoProperties;
import com.aparcar.api.config.DiscapacidadProperties;
import com.aparcar.api.filters.AccesoRateLimitFilter;
import com.aparcar.api.filters.VerificacionRateLimitFilter;
import com.aparcar.api.filters.JWTGeneratorFilter;
import com.aparcar.api.filters.JWTValidationFilter;
import com.aparcar.api.filters.RateLimitFilter;
import com.aparcar.api.security.CustomAccessDeniedHandler;
import com.aparcar.api.security.CustomBasicAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.IpAddressAuthorizationManager;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;

import java.util.Arrays;
import java.util.List;

import static com.aparcar.api.config.ApplicationConstants.*;

/**
 * Security configuration for all non-development environments (production and
 * test).
 * Provides stricter security settings, production CORS, and a higher BCrypt
 * strength.
 */
@Configuration
@Profile(NOT_DEV_ENV)
public class ProdSecurityConfig {

    /**
     * Configures the SecurityContextRepository to support both Request Attributes
     * and Session.
     * This is crucial for MockMvc and @WithMockUser to function correctly in a
     * stateless environment.
     *
     * @return A DelegatingSecurityContextRepository combining multiple strategies.
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }

    /**
     * Configures the security filter chain for production profile.
     * Includes JWT filters, rate limiting, and basic authentication with production
     * CORS settings.
     *
     * @param http                      The HttpSecurity builder.
     * @param revokedUserCache          Cache for revoked tokens.
     * @param securityContextRepository The repository for capturing security
     *                                  context.
     * @param env                       The Spring environment for accessing
     *                                  properties.
     * @param discapacidadProperties    Limite de subidas de certificados.
     * @param accesoProperties          Reglas del control de acceso (rate limit
     *                                  de /api/v1/accesos/**).
     * @return The configured SecurityFilterChain.
     */
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            IRevokedUserCache revokedUserCache,
            SecurityContextRepository securityContextRepository,
            Environment env,
            AccesoProperties accesoProperties,
            DiscapacidadProperties discapacidadProperties) {

        String secret = env.getProperty(JWT_SECRET_KEY, JWT_SECRET_DEFAULT);
        boolean isDev = Arrays.asList(env.getActiveProfiles()).contains(DEV_ENV);

        JWTValidationFilter jwtValidationFilter =
                new JWTValidationFilter(revokedUserCache, secret, isDev);

        JWTGeneratorFilter jwtGeneratorFilter =
                new JWTGeneratorFilter(secret);

        RateLimitFilter rateLimitFilter =
                new RateLimitFilter();

        AccesoRateLimitFilter accesoRateLimitFilter =
                new AccesoRateLimitFilter(accesoProperties.rateLimitPorMinuto());

        VerificacionRateLimitFilter verificacionRateLimitFilter =
                new VerificacionRateLimitFilter(discapacidadProperties.subidasPorHora());

        return http
                .securityContext(context ->
                        context.securityContextRepository(securityContextRepository))
                .sessionManagement(config ->
                        config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(corsCustomizer ->
                        corsCustomizer.configurationSource(request -> {
                            CorsConfiguration config = new CorsConfiguration();

                            config.setAllowedOriginPatterns(origenesPermitidos(env));

                            config.setAllowedMethods(
                                    List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

                            config.setAllowedHeaders(
                                    List.of("Authorization", "Content-Type", "Accept"));

                            config.setExposedHeaders(
                                    List.of("Authorization"));

                            config.setAllowCredentials(true);
                            config.setMaxAge(3600L);

                            return config;
                        }))
                .csrf(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(requests -> requests

                        // Leer precios y cotizar lo necesita cualquiera que
                        // arme una reserva; cambiarlos es configuracion y
                        // queda solo para ADMIN.
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/tarifas", "/api/v1/tarifas/cotizacion")
                        .hasAnyAuthority("ADMIN", "EMPLEADO", "USER")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/tarifas/cotizacion")
                        .hasAnyAuthority("ADMIN", "EMPLEADO", "USER")
                        .requestMatchers("/api/v1/tarifas", "/api/v1/tarifas/**").hasAuthority("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/convenios")
                        .hasAnyAuthority("ADMIN", "EMPLEADO", "USER")
                        .requestMatchers("/api/v1/convenios/credenciales", "/api/v1/convenios/credenciales/**", "/api/v1/convenios/verificar")
                        .hasAnyAuthority("ADMIN", "EMPLEADO", "USER")
                        .requestMatchers("/api/v1/convenios", "/api/v1/convenios/**").hasAuthority("ADMIN")

                        // Endpoints públicos. Van primero para que
                        // /api/v1/cocheras/disponibles se resuelva acá y no
                        // caiga en la regla de ADMIN de /api/v1/cocheras/** de abajo.
                        .requestMatchers(
                                "/api/v1/cocheras/disponibles",
                                "/register",
                                "/forgot-password",
                                "/reset-password",
                                // Docker la consulta sin credenciales para
                                // saber si el contenedor sigue vivo, y lo
                                // reinicia cuando no responde. Pidiendo
                                // autenticacion, el 401 se leia como "caido" y
                                // el contenedor se reiniciaba sin parar.
                                //
                                // No expone nada: devuelve solo {"status":"UP"}.
                                // El resto de /actuator/** sigue limitado a
                                // localhost por la regla de mas abajo.
                                "/actuator/health"
                        ).permitAll()
                        // Los datos propios del visitante van primero: si no,
                        // caerian en la regla de ADMIN de /api/v1/visitantes/**
                        // que esta abajo.
                        .requestMatchers(
                                "/api/v1/visitantes/me",
                                "/api/v1/visitantes/me/**"
                        ).authenticated()

                        // Verificacion de discapacidad: cada cuenta sube su
                        // certificado y ve lo suyo; revisar la cola y abrir
                        // certificados ajenos es solo del ADMIN (un dato de
                        // salud: el EMPLEADO no lo necesita para operar).
                        .requestMatchers(
                                "/api/v1/verificaciones-discapacidad/mia",
                                "/api/v1/verificaciones-discapacidad/mia/**"
                        ).authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                "/api/v1/verificaciones-discapacidad"
                        ).authenticated()
                        .requestMatchers(
                                "/api/v1/verificaciones-discapacidad",
                                "/api/v1/verificaciones-discapacidad/**"
                        ).hasAuthority("ADMIN")

                        // Puntos: cada cuenta ve su billetera y sus movimientos;
                        // el resumen del programa es solo del ADMIN. Ningun
                        // endpoint acredita: se suman al registrar el ingreso.
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/puntos/resumen")
                        .hasAuthority("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/v1/puntos/billetera",
                                "/api/v1/puntos/movimientos"
                        ).authenticated()
                        .requestMatchers("/api/v1/puntos", "/api/v1/puntos/**").denyAll()

                        // Validar codigos de acceso (ingreso y egreso) es la
                        // tarea central del personal en la puerta.
                        .requestMatchers("/api/v1/accesos/**").hasAnyAuthority("ADMIN", "EMPLEADO")
                        .requestMatchers("/api/v1/dashboard", "/api/v1/dashboard/**").hasAuthority("ADMIN")

                        // --- Regimen de faltas ---
                        // Lo propio lo ve cualquiera; va primero para que no
                        // caiga en la regla del personal de mas abajo.
                        .requestMatchers("/api/v1/amonestaciones/mias").authenticated()
                        // Los numeros del regimen los fija el administrador.
                        .requestMatchers("/api/v1/amonestaciones/configuracion")
                        .hasAuthority("ADMIN")
                        // Levantar un bloqueo es una decision del administrador,
                        // aunque la falta la pueda aplicar un empleado.
                        .requestMatchers("/api/v1/amonestaciones/visitante/*/restituir")
                        .hasAuthority("ADMIN")
                        // Aplicar faltas y consultar las de otro: el personal.
                        .requestMatchers("/api/v1/amonestaciones", "/api/v1/amonestaciones/**")
                        .hasAnyAuthority("ADMIN", "EMPLEADO")

                        // Las series recurrentes las arma solo el ADMIN:
                        // va antes que la regla general de /api/v1/reservas/**.
                        .requestMatchers("/api/v1/reservas/recurrentes", "/api/v1/reservas/recurrentes/**")
                        .hasAuthority("ADMIN")

                        // Consultar la grilla de cocheras y la lista de
                        // visitantes es parte de operar: el personal lo
                        // necesita para la pantalla de ocupacion y para
                        // reservar a nombre de otro. Cambiar el estado de una
                        // cochera, en cambio, es configuracion.
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/v1/cocheras", "/api/v1/visitantes")
                        .hasAnyAuthority("ADMIN", "EMPLEADO")
                        .requestMatchers("/api/v1/visitantes/alta").hasAnyAuthority("ADMIN", "EMPLEADO")

                        // Solo ADMIN: las cuentas, los precios y el estado de
                        // las cocheras. Todo lo que configura el sistema en vez
                        // de operarlo.
                        .requestMatchers(
                                "/users/**",
                                "/api/v1/usuarios/**",
                                "/api/v1/cocheras/**",
                                "/api/v1/visitantes/**"
                        ).hasAuthority("ADMIN")
                        // Cualquier usuario autenticado.

                        .requestMatchers(
                                "/api/v1/reservas/**",
                                "/api/v1/vehiculos/**",
                                "/login"
                        ).authenticated()

                        .requestMatchers("/actuator/**")
                        .access((authentication, ctx) -> {
                            if (ctx == null) {
                                return new AuthorizationDecision(false);
                            }

                            List<String> localhostIps =
                                    List.of("127.0.0.1/32", "::1/128");

                            for (String ip : localhostIps) {
                                IpAddressAuthorizationManager mgr =
                                        IpAddressAuthorizationManager.hasIpAddress(ip);

                                var result =
                                        mgr.authorize(authentication, ctx);

                                if (result.isGranted()) {
                                    return new AuthorizationDecision(true);
                                }
                            }

                            return new AuthorizationDecision(false);
                        }))

                .exceptionHandling(handling -> handling
                        .accessDeniedHandler(new CustomAccessDeniedHandler()))

                .addFilterAfter(
                        jwtGeneratorFilter,
                        BasicAuthenticationFilter.class)

                .addFilterBefore(
                        jwtValidationFilter,
                        BasicAuthenticationFilter.class)

                .addFilterBefore(
                        rateLimitFilter,
                        JWTValidationFilter.class)

                // Despues del JWT, para contar por operador y no por IP.
                .addFilterAfter(
                        accesoRateLimitFilter,
                        JWTValidationFilter.class)

                // Tambien cuenta por cuenta: despues del JWT.
                .addFilterAfter(
                        verificacionRateLimitFilter,
                        AccesoRateLimitFilter.class)

                .httpBasic(config ->
                        config.authenticationEntryPoint(
                                new CustomBasicAuthenticationEntryPoint()))

                .build();
    }
    /**
     * Provides a BCryptPasswordEncoder with strength 12 for production password
     * hashing.
     *
     * @return A BCryptPasswordEncoder instance with higher security rounds.
     */

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Los origenes que el navegador tiene permitido usar contra esta API.
     *
     * <p>Salen de la variable {@code CORS_ALLOWED_ORIGINS}, separados por coma,
     * porque el dominio del frontend lo decide el despliegue y no se conoce al
     * compilar. Antes estaba escrito en el codigo apuntando a un dominio que
     * nunca existio, y eso devolvia 403 en todo pedido con cabecera Origin.
     *
     * <p>Se aceptan patrones con {@code *} (de ahi
     * {@code setAllowedOriginPatterns}). Nunca devuelve {@code *} a secas: con
     * {@code allowCredentials} activo el navegador lo rechaza, asi que un
     * descuido se nota enseguida en vez de abrir la API a cualquiera.
     */
    private static List<String> origenesPermitidos(Environment env) {
        String configurados = env.getProperty(CORS_ALLOWED_ORIGINS_KEY);

        if (configurados == null || configurados.isBlank()) {
            return List.of(CORS_ALLOWED_ORIGINS_DEFAULT);
        }

        return Arrays.stream(configurados.split(","))
                .map(String::trim)
                .filter(origen -> !origen.isEmpty())
                .toList();
    }
}
