package com.aparcar.api.integration;

import com.aparcar.api.component.IRevokedUserCache;
import com.aparcar.api.config.IntegrationTests;
import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.repository.VisitanteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTests
@Transactional
class PerfilControllerTests {
    @Autowired MockMvc mvc;
    @Autowired VisitanteRepository visitantes;
    @Autowired IRevokedUserCache revoked;
    private Visitante admin;
    private Visitante visitante;

    @BeforeEach
    void setup() {
        revoked.clear();
        admin = visitantes.save(new Visitante("Ana", "30000111", "perfil-admin@test.com", "hash", "111", Set.of(AppAuthority.ADMIN), true));
        visitante = visitantes.save(new Visitante("Juan", "30000222", "perfil-user@test.com", "hash", null, Set.of(AppAuthority.USER), true));
    }

    @Test
    void perfilRequiereSesion() throws Exception {
        mvc.perform(get("/api/v1/visitantes/me")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/visitantes/me").contentType(MediaType.APPLICATION_JSON)
                .content("{}" )).andExpect(status().isUnauthorized());
    }

    @Test
    void adminEditaSusDatosYSuEstacionamientoSinModificarOtrasCuentas() throws Exception {
        mvc.perform(put("/api/v1/visitantes/me").with(user(admin.getEmail()).authorities(() -> "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"email":" PERFIL-ADMIN@TEST.COM ","documento":" 30000333 ",
                     "telefono":" 222 ","nombreEstacionamiento":" Parking Larrea "}
                    """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.documento").value("30000333"))
                .andExpect(jsonPath("$.telefono").value("222"))
                .andExpect(jsonPath("$.nombreEstacionamiento").value("Parking Larrea"));
        mvc.perform(get("/api/v1/visitantes/me").with(user(admin.getEmail()).authorities(() -> "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombreEstacionamiento").value("Parking Larrea"));
        assertEquals("30000222", visitantes.findById(visitante.getId()).orElseThrow().getDocumento());
        assertNull(visitante.getNombreEstacionamiento());
        assertEquals(Set.of(AppAuthority.ADMIN), admin.getAuthorities());
        assertFalse(revoked.isRevoked(admin.getEmail()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"documento\":\"123\"", "\"nombreEstacionamiento\":\"No permitido\""})
    void userNoPuedeModificarCamposDeAdmin(String campo) throws Exception {
        mvc.perform(put("/api/v1/visitantes/me").with(user(visitante.getEmail()).authorities(() -> "USER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"perfil-user@test.com\"," + campo + "}"))
                .andExpect(status().isForbidden());
        assertEquals("30000222", visitante.getDocumento());
        assertNull(visitante.getNombreEstacionamiento());
    }

    @Test
    void userConservaContactoYDeclaracionAccesible() throws Exception {
        mvc.perform(put("/api/v1/visitantes/me").with(user(visitante.getEmail()).authorities(() -> "USER"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"email":"perfil-user@test.com","telefono":"","tieneDiscapacidad":true}
                    """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tieneDiscapacidad").value(true));
        assertEquals("30000222", visitante.getDocumento());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"documento\":\"30000222\"", "\"email\":\" PERFIL-USER@TEST.COM \""})
    void rechazaIdentidadesDuplicadasInclusoEnCuentasInactivas(String campo) throws Exception {
        visitante.setIsActive(false);
        visitantes.saveAndFlush(visitante);
        String body = campo.startsWith("\"email\"") ? "{" + campo + "}"
                : "{\"email\":\"perfil-admin@test.com\"," + campo + "}";
        mvc.perform(put("/api/v1/visitantes/me").with(user(admin.getEmail()).authorities(() -> "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        assertEquals("perfil-admin@test.com", admin.getEmail());
        assertEquals("30000111", admin.getDocumento());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"documento\":\"   \"", "\"email\":\" \"", "\"email\":\"invalido\""})
    void rechazaDatosInvalidos(String campo) throws Exception {
        String body = campo.startsWith("\"email\"") ? "{" + campo + "}"
                : "{\"email\":\"perfil-admin@test.com\"," + campo + "}";
        mvc.perform(put("/api/v1/visitantes/me").with(user(admin.getEmail()).authorities(() -> "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }

    @Test
    void limitaElNombreYCambiaAVacioSinPerderDatos() throws Exception {
        admin.setNombreEstacionamiento("Anterior");
        visitantes.saveAndFlush(admin);
        mvc.perform(put("/api/v1/visitantes/me").with(user(admin.getEmail()).authorities(() -> "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"perfil-admin@test.com\",\"nombreEstacionamiento\":\"" + "x".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/visitantes/me").with(user(admin.getEmail()).authorities(() -> "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"perfil-admin@test.com\",\"nombreEstacionamiento\":\"  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombreEstacionamiento").isEmpty());
        assertEquals("30000111", admin.getDocumento());
    }

    @Test
    void clientesAnterioresNoBorranNombreNiDocumento() throws Exception {
        admin.setNombreEstacionamiento("Parking Larrea");
        visitantes.saveAndFlush(admin);
        mvc.perform(put("/api/v1/visitantes/me").with(user(admin.getEmail()).authorities(() -> "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"perfil-admin@test.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombreEstacionamiento").value("Parking Larrea"))
                .andExpect(jsonPath("$.documento").value("30000111"));
    }
}
