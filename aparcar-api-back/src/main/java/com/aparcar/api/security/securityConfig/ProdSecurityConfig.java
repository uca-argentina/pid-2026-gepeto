package com.aparcar.api.security.securityConfig;

import com.aparcar.api.component.IRevokedUserCache;
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
     * @return The configured SecurityFilterChain.
     */
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            IRevokedUserCache revokedUserCache,
            SecurityContextRepository securityContextRepository,
            Environment env) {

        String secret = env.getProperty(JWT_SECRET_KEY, JWT_SECRET_DEFAULT);
        boolean isDev = Arrays.asList(env.getActiveProfiles()).contains(DEV_ENV);

        JWTValidationFilter jwtValidationFilter =
                new JWTValidationFilter(revokedUserCache, secret, isDev);

        JWTGeneratorFilter jwtGeneratorFilter =
                new JWTGeneratorFilter(secret);

        RateLimitFilter rateLimitFilter =
                new RateLimitFilter();

        return http
                .securityContext(context ->
                        context.securityContextRepository(securityContextRepository))
                .sessionManagement(config ->
                        config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(corsCustomizer ->
                        corsCustomizer.configurationSource(request -> {
                            CorsConfiguration config = new CorsConfiguration();

                            // TODO: reemplazar por el dominio real del frontend una vez desplegado
                            config.setAllowedOriginPatterns(
                                    List.of("https://*.aparcar.com.ar"));

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

                        // Endpoints públicos. Van primero para que
                        // /api/v1/cocheras/disponibles se resuelva acá y no
                        // caiga en la regla de ADMIN de /api/v1/cocheras/** de abajo.
                        .requestMatchers(
                                "/api/v1/cocheras/disponibles",
                                "/register",
                                "/forgot-password",
                                "/reset-password"
                        ).permitAll()
                        // Los datos propios del visitante van primero: si no,
                        // caerian en la regla de ADMIN de /api/v1/visitantes/**
                        // que esta abajo.
                        .requestMatchers(
                                "/api/v1/visitantes/me",
                                "/api/v1/visitantes/me/**"
                        ).authenticated()

                        // Solo ADMIN puede gestionar usuarios, dar de alta
                        // visitantes con reserva y
                        // administrar cocheras.

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
                                "/login",
                                "/actuator/health"
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
}