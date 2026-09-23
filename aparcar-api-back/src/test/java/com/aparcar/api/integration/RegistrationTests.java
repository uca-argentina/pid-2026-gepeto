package com.aparcar.api.integration;

import com.aparcar.api.config.IntegrationTests;
import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.repository.VisitanteRepository;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.repository.VehiculoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTests
class RegistrationTests {
    @Autowired MockMvc mvc;
    @Autowired VisitanteRepository visitantes;
    @Autowired VehiculoRepository vehiculos;
    @Autowired ReservaRepository reservas;
    @Autowired PasswordEncoder encoder;

    @AfterEach
    void cleanup() {
        visitantes.deleteAll();
    }

    private String body(String documento, String email) {
        return """
                {"nombre":" Ana Pérez ","documento":"%s","email":"%s",
                 "telefono":"12345678","password":"Contraseña123🔑",
                 "authorities":["ADMIN"],"isActive":false}
                """.formatted(documento, email);
    }

    @Test
    void anonymousRegistrationCreatesOnlyActiveUserVisibleToAdmin() throws Exception {
        long vehiclesBefore = vehiculos.count();
        long bookingsBefore = reservas.count();
        mvc.perform(post("/register").servletPath("/register")
                        .header("Authorization", "Bearer expired-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body(" 30111222 ", " Ana@Example.com ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.documento").value("30111222"))
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.authorities.length()").value(1))
                .andExpect(jsonPath("$.authorities[0]").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        Visitante account = visitantes.findByEmail("ana@example.com").orElseThrow();
        assertTrue(account.getIsActive());
        assertTrue(encoder.matches("Contraseña123🔑", account.getPassword()));
        assertEquals(Set.of(AppAuthority.USER), account.getAuthorities());
        assertEquals(vehiclesBefore, vehiculos.count());
        assertEquals(bookingsBefore, reservas.count());

        mvc.perform(get("/api/v1/usuarios").with(user("admin@example.com").authorities(() -> "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("ana@example.com"))
                .andExpect(jsonPath("$[0].password").doesNotExist());
        mvc.perform(get("/api/v1/visitantes/me").with(user(account.getEmail()).authorities(() -> "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documento").value("30111222"));
        mvc.perform(get("/api/v1/usuarios").with(user(account.getEmail()).authorities(() -> "USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsExistingEmailRegardlessOfCaseAndSpaces() throws Exception {
        visitantes.save(new Visitante("Existente", "12345678", "Ana@Example.com", "hash", null, Set.of(AppAuthority.USER), true));
        mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON).content(body("30111222", " ANA@example.com ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ya existe una cuenta asociada a ese email."));
        assertEquals(1, visitantes.count());
    }

    @Test
    void rejectsExistingDocumentEvenForInactiveAccounts() throws Exception {
        visitantes.save(new Visitante("Existente", "30111222", "otro@example.com", "hash", null, Set.of(AppAuthority.USER), false));
        mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON).content(body(" 30111222 ", "ana@example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ya existe un visitante con ese documento."));
        assertEquals(1, visitantes.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"nombre\":\"   \"", "\"documento\":\"   \"", "\"email\":\"invalid\"",
            "\"password\":\"123\"", "\"password\":\"        \""
    })
    void rejectsInvalidInput(String replacement) throws Exception {
        String field = replacement.substring(0, replacement.indexOf(':'));
        String payload = body("30111222", "ana@example.com").replaceAll(field + ":\"[^\"]*\"", replacement);
        mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());
        assertEquals(0, visitantes.count());
    }

    @Test
    void rejectsPasswordsOverBcryptByteLimit() throws Exception {
        String payload = body("30111222", "ana@example.com").replace("Contraseña123🔑", "á".repeat(37));
        mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());
        assertEquals(0, visitantes.count());
    }
}
