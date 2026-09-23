package com.aparcar.api.integration;

import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.repository.VisitanteRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import javax.crypto.SecretKey;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static com.aparcar.api.config.ApplicationConstants.JWT_SECRET_DEFAULT;
import static com.aparcar.api.config.ApplicationConstants.TEST_ENV;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Caja negra: ejercita el login real de punta a punta contra un servidor
 * embebido real (no MockMvc simulado), verificando la respuesta HTTP
 * completa — status, presencia/ausencia del JWT y su contenido.
 *
 * Usa un servidor real (RANDOM_PORT) en vez de MockMvc a propósito:
 * RateLimitFilter, JWTValidationFilter y JWTGeneratorFilter deciden si
 * aplican mirando {@code request.getServletPath()}, que en el dispatch
 * simulado de MockMvc no coincide con el de un despliegue real (queda
 * vacío), haciendo que esos filtros nunca se activen y dando falsos
 * negativos. Con servidor real se reproduce el comportamiento tal cual se
 * ve en producción.
 *
 * Nota sobre CORS: no se verifican acá los headers Access-Control-* — en
 * este entorno de test (servidor embebido sin verdadero front-controller
 * de servlet completo) no se reproducen de forma confiable, a diferencia
 * del comportamiento real ya verificado a mano contra la app corriendo en
 * Docker (con navegador y con curl/PowerShell), donde sí funcionan.
 *
 * Nota sobre rate limiting: /login comparte el bucket por IP (capacidad 5
 * por minuto) con /register y /forgot-password, y con servidor real ese
 * límite sí se aplica de verdad. Esta clase hace 4 requests reales a
 * /login en total (una por test), por debajo del límite, para no
 * contaminar los resultados.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(TEST_ENV)
class LoginFlowTests {

    @Autowired
    private VisitanteRepository visitanteRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;

    @BeforeEach
    void setUpRestTemplate() {
        restTemplate = new RestTemplate();
        // Por defecto RestTemplate tira excepción ante 4xx/5xx; acá queremos
        // inspeccionar el status/headers de la respuesta como en cualquier
        // request normal, sin importar si fue un error.
        restTemplate.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
            // handleError() no se sobrescribe: nunca se llama porque
            // hasError() siempre es false, y el default de la interfaz
            // (Spring 7) alcanza sin problema.
        });
    }

    @AfterEach
    void tearDown() {
        visitanteRepository.deleteAll();
    }

    private void saveUser(String email, String password, Set<AppAuthority> authorities) {
        Visitante user = new Visitante();
        user.setNombre("Test User");
        // El documento es obligatorio y unico: lo derivamos del email para que
        // cada cuenta de prueba tenga el suyo.
        user.setDocumento(String.valueOf(Math.abs(email.hashCode())));
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setAuthorities(authorities);
        user.setIsActive(true);
        visitanteRepository.save(user);
    }

    private ResponseEntity<String> login(String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        String credentials = Base64.getEncoder().encodeToString((email + ":" + password).getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + credentials);
        RequestEntity<Void> request = RequestEntity
                .method(HttpMethod.POST, URI.create("http://localhost:" + port + "/login"))
                .headers(headers)
                .build();
        return restTemplate.exchange(request, String.class);
    }

    @Test
    @DisplayName("login con credenciales válidas devuelve 200 y un JWT con el email y las authorities del usuario")
    void loginWithValidCredentialsReturnsJwtWithClaims() {
        saveUser("mateo@mateo.com", "password-correcta", Set.of(AppAuthority.USER, AppAuthority.ADMIN));

        ResponseEntity<String> response = login("mateo@mateo.com", "password-correcta");

        assertEquals(HttpStatus.OK, response.getStatusCode());

        String authHeader = response.getHeaders().getFirst("Authorization");
        assertTrue(authHeader != null && authHeader.startsWith("Bearer "));

        String jwt = authHeader.substring(7);
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET_DEFAULT.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(jwt).getPayload();

        assertEquals("mateo@mateo.com", claims.get("email"));
        List<String> authorities = List.of(String.valueOf(claims.get("authorities")).split(","));
        assertEquals(2, authorities.size());
        assertTrue(authorities.contains("USER"));
        assertTrue(authorities.contains("ADMIN"));
    }

    @Test
    @DisplayName("login con un usuario que solo tiene rol USER devuelve un JWT con una sola authority")
    void loginWithUserOnlyRoleReturnsJwtWithSingleAuthority() {
        saveUser("solo-user@mateo.com", "password-correcta", Set.of(AppAuthority.USER));

        ResponseEntity<String> response = login("solo-user@mateo.com", "password-correcta");

        assertEquals(HttpStatus.OK, response.getStatusCode());

        String jwt = response.getHeaders().getFirst("Authorization").substring(7);
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET_DEFAULT.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(jwt).getPayload();

        assertEquals("USER", claims.get("authorities"));
    }

    @Test
    @DisplayName("login con email inexistente devuelve 401 sin JWT")
    void loginWithNonExistentEmailReturnsUnauthorized() {
        ResponseEntity<String> response = login("no-existe@mateo.com", "cualquier-cosa");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNull(response.getHeaders().getFirst("Authorization"));
    }

    @Test
    @DisplayName("en el perfil de test/dev, una contraseña incorrecta devuelve 401 sin JWT")
    void loginRejectsWrongPasswordInDevProfile() {
        saveUser("mateo@test.com", "password-correcta", Set.of(AppAuthority.USER));

        ResponseEntity<String> response = login("mateo@test.com", "esta-password-no-es-la-real");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNull(response.getHeaders().getFirst("Authorization"));
    }
}
