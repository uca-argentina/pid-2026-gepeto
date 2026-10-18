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
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.aparcar.api.config.ApplicationConstants.*;

/**
 * Security configuration for the development environment only.
 * Provides relaxed CORS settings (localhost) and a lower BCrypt strength.
 */
@Configuration
@Profile(DEV_ENV)
public class DevSecurityConfig {

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
     * Configures the security filter chain for dev/test profiles.
     * Includes JWT filters, rate limiting, and basic authentication.
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

                            // CORS_ORIGENES_EXTRA (separados por coma) suma
                            // origenes para probar desde otro dispositivo: la
                            // IP local de la maquina o un tunel HTTPS, que es
                            // lo que necesita la camara del celular.
                            List<String> origenes = new ArrayList<>(List.of("http://localhost:*"));
                            Arrays.stream(env.getProperty("CORS_ORIGENES_EXTRA", "").split(","))
                                    .map(String::trim)
                                    .filter(origen -> !origen.isEmpty())
                                    .forEach(origenes::add);
                            config.setAllowedOriginPatterns(origenes);

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

                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/tarifas", "/api/v1/tarifas/cotizacion")
                        .hasAnyAuthority("ADMIN", "EMPLEADO", "USER")
                        .requestMatchers("/api/v1/tarifas", "/api/v1/tarifas/**").hasAuthority("ADMIN")

                        .requestMatchers(
                                "/api/v1/cocheras/disponibles",
                                "/register",
                                "/forgot-password",
                                "/reset-password",
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

                        // Validar codigos de acceso (ingreso y egreso) es la
                        // tarea central del personal en la puerta.
                        .requestMatchers("/api/v1/accesos/**").hasAnyAuthority("ADMIN", "EMPLEADO")
                        .requestMatchers("/api/v1/dashboard", "/api/v1/dashboard/**").hasAuthority("ADMIN")

                        // Las series recurrentes las arma solo el ADMIN:
                        // va antes que la regla general de /api/v1/reservas/**.
                        .requestMatchers("/api/v1/reservas/recurrentes", "/api/v1/reservas/recurrentes/**")
                        .hasAuthority("ADMIN")

                        // Consultar la grilla de cocheras y la lista de
                        // visitantes es parte de operar: el personal lo
                        // necesita para la pantalla de ocupacion y para
                        // reservar a nombre de otro.
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/v1/cocheras", "/api/v1/visitantes")
                        .hasAnyAuthority("ADMIN", "EMPLEADO")
                        .requestMatchers("/api/v1/visitantes/alta").hasAnyAuthority("ADMIN", "EMPLEADO")

                        // Solo ADMIN: las cuentas, los precios y el estado de
                        // las cocheras. Lo que configura el sistema en vez de
                        // operarlo.
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

                        .requestMatchers("/**").permitAll())

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
     * Provides a BCryptPasswordEncoder for password hashing.
     *
     * @return A BCryptPasswordEncoder instance.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
