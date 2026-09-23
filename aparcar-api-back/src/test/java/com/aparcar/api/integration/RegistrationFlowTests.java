package com.aparcar.api.integration;

import com.aparcar.api.repository.VisitanteRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static com.aparcar.api.config.ApplicationConstants.JWT_SECRET_DEFAULT;
import static com.aparcar.api.config.ApplicationConstants.TEST_ENV;
import static org.junit.jupiter.api.Assertions.*;

// Servidor real: ejercita los filtros JWT y rate limit con servletPath real.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:registration-flow")
@ActiveProfiles(TEST_ENV)
@DirtiesContext
class RegistrationFlowTests {
    @LocalServerPort int port;
    @Autowired VisitanteRepository visitantes;

    @AfterEach
    void cleanup() {
        visitantes.deleteAll();
    }

    @Test
    void registerLoginAndAccessOwnProfileWithRealJwt() {
        RestTemplate client = new RestTemplate();
        client.setErrorHandler(new ResponseErrorHandler() {
            @Override public boolean hasError(ClientHttpResponse response) { return false; }
        });
        String base = "http://localhost:" + port;
        ResponseEntity<String> registration = client.exchange(RequestEntity.post(URI.create(base + "/register"))
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"nombre":"Ana Pérez","documento":"30111222","email":" Ana@Example.com ",
                         "password":"Contraseña123🔑","authorities":["ADMIN"]}
                        """), String.class);
        assertEquals(HttpStatus.CREATED, registration.getStatusCode());

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("ANA@example.com", "Contraseña123🔑", StandardCharsets.UTF_8);
        ResponseEntity<String> login = client.exchange(RequestEntity.post(URI.create(base + "/login"))
                .headers(headers).build(), String.class);
        assertEquals(HttpStatus.OK, login.getStatusCode());
        String authorization = login.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        assertNotNull(authorization);
        var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(JWT_SECRET_DEFAULT.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(authorization.substring(7)).getPayload();
        assertEquals("USER", claims.get("authorities"));
        assertEquals("ana@example.com", claims.get("email"));

        ResponseEntity<String> profile = client.exchange(RequestEntity.get(URI.create(base + "/api/v1/visitantes/me"))
                .header(HttpHeaders.AUTHORIZATION, authorization).build(), String.class);
        assertEquals(HttpStatus.OK, profile.getStatusCode());
        assertTrue(profile.getBody().contains("30111222"));
        ResponseEntity<String> admin = client.exchange(RequestEntity.get(URI.create(base + "/api/v1/usuarios"))
                .header(HttpHeaders.AUTHORIZATION, authorization).build(), String.class);
        assertEquals(HttpStatus.FORBIDDEN, admin.getStatusCode());

        // Registro y login consumieron dos de los cinco intentos por minuto.
        for (int i = 0; i < 4; i++) {
            ResponseEntity<String> response = client.exchange(RequestEntity.post(URI.create(base + "/register"))
                    .contentType(MediaType.APPLICATION_JSON).body("{}"), String.class);
            assertEquals(i < 3 ? HttpStatus.BAD_REQUEST : HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        }
    }
}
