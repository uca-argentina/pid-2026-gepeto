package com.aparcar.api.integration;

import com.aparcar.api.component.IRevokedUserCache;
import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.repository.VisitanteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Set;
import static com.aparcar.api.config.ApplicationConstants.TEST_ENV;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:perfil-session")
@ActiveProfiles(TEST_ENV)
@DirtiesContext
class PerfilSessionTests {
    @LocalServerPort int port;
    @Autowired VisitanteRepository visitantes;
    @Autowired PasswordEncoder encoder;
    @Autowired IRevokedUserCache revoked;

    @AfterEach
    void cleanup() {
        visitantes.deleteAll();
        revoked.clear();
    }

    @Test
    void cambiarEmailPersistePerfilRevocaJwtAnteriorYPermiteLoginNuevo() {
        var cuenta = visitantes.save(new Visitante("Admin", "30000999", "antes@test.com",
                encoder.encode("ClaveSegura123"), "111", Set.of(AppAuthority.ADMIN), true));
        var client = new RestTemplate();
        client.setErrorHandler(new ResponseErrorHandler() {
            @Override public boolean hasError(ClientHttpResponse response) { return false; }
        });
        String base = "http://localhost:" + port;
        var auth = new HttpHeaders();
        auth.setBasicAuth("antes@test.com", "ClaveSegura123");
        var login = client.exchange(RequestEntity.post(URI.create(base + "/login")).headers(auth).build(), String.class);
        assertEquals(HttpStatus.OK, login.getStatusCode());
        String jwtAnterior = login.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        assertNotNull(jwtAnterior);
        var guardado = client.exchange(RequestEntity.put(URI.create(base + "/api/v1/visitantes/me"))
                .header(HttpHeaders.AUTHORIZATION, jwtAnterior).contentType(MediaType.APPLICATION_JSON)
                .body("""
                    {"email":"despues@test.com","documento":"30000888","telefono":"222","nombreEstacionamiento":"Parking Larrea"}
                    """), String.class);
        assertEquals(HttpStatus.OK, guardado.getStatusCode());
        assertEquals(cuenta.getId(), visitantes.findByEmail("despues@test.com").orElseThrow().getId());
        assertTrue(visitantes.findByEmail("antes@test.com").isEmpty());

        var anterior = client.exchange(RequestEntity.get(URI.create(base + "/api/v1/visitantes/me"))
                .header(HttpHeaders.AUTHORIZATION, jwtAnterior).build(), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, anterior.getStatusCode());
        auth.setBasicAuth("despues@test.com", "ClaveSegura123");
        var nuevoLogin = client.exchange(RequestEntity.post(URI.create(base + "/login")).headers(auth).build(), String.class);
        assertEquals(HttpStatus.OK, nuevoLogin.getStatusCode());
        var perfil = client.exchange(RequestEntity.get(URI.create(base + "/api/v1/visitantes/me"))
                .header(HttpHeaders.AUTHORIZATION, nuevoLogin.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).build(), String.class);
        assertEquals(HttpStatus.OK, perfil.getStatusCode());
        assertTrue(perfil.getBody().contains("Parking Larrea"));
        assertTrue(perfil.getBody().contains("30000888"));
        assertEquals(Set.of(AppAuthority.ADMIN), visitantes.findById(cuenta.getId()).orElseThrow().getAuthorities());

        // Volver al email original debe permitir un nuevo login, sin reactivar
        // el primer JWT ni bloquear la cuenta durante las ocho horas de la caché.
        var volver = client.exchange(RequestEntity.put(URI.create(base + "/api/v1/visitantes/me"))
                .header(HttpHeaders.AUTHORIZATION, nuevoLogin.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .contentType(MediaType.APPLICATION_JSON).body("{\"email\":\"antes@test.com\"}"), String.class);
        assertEquals(HttpStatus.OK, volver.getStatusCode());
        auth.setBasicAuth("antes@test.com", "ClaveSegura123");
        var loginOriginal = client.exchange(RequestEntity.post(URI.create(base + "/login"))
                .headers(auth).build(), String.class);
        assertEquals(HttpStatus.OK, loginOriginal.getStatusCode());
        var perfilOriginal = client.exchange(RequestEntity.get(URI.create(base + "/api/v1/visitantes/me"))
                .header(HttpHeaders.AUTHORIZATION, loginOriginal.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .build(), String.class);
        assertEquals(HttpStatus.OK, perfilOriginal.getStatusCode());
        var sesionVieja = client.exchange(RequestEntity.get(URI.create(base + "/api/v1/visitantes/me"))
                .header(HttpHeaders.AUTHORIZATION, jwtAnterior).build(), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, sesionVieja.getStatusCode());
    }
}
