package com.aparcar.api.integration;

import com.aparcar.api.config.IntegrationTests;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;
import static org.springframework.security.core.context.SecurityContextHolder.getContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Caja negra: matriz de quién puede pegarle a qué endpoint, tal como lo
 * necesitan /dashboard-user y /dashboard-admin del frontend.
 *
 * Importante: /api/v1/vehiculos y /api/v1/reservas los usan AMBOS
 * dashboards, así que a nivel backend solo exigen estar autenticado; lo que
 * cambia según el rol es el CONTENIDO (un visitante ve lo suyo, el admin ve
 * todo), no el acceso. Estos tests dejan eso documentado: si alguien más
 * adelante le agrega hasAuthority("ADMIN") a uno de estos por error,
 * rompería al dashboard-user sin que sea obvio por qué.
 *
 * El catálogo /api/v1/visitantes sí es exclusivo del ADMIN, porque un
 * visitante solo reserva a su nombre y no necesita ver a los demás; sus
 * propios datos los pide por /api/v1/visitantes/me.
 */
@IntegrationTests
class DashboardAccessSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    // ---- Endpoints compartidos por dashboard-user y dashboard-admin ----

    @Test
    @WithAnonymousUser
    @DisplayName("GET /api/v1/visitantes, /vehiculos y /reservas devuelven 401 para anónimos")
    void sharedEndpointsRejectAnonymousUsers() throws Exception {
        mockMvc.perform(get("/api/v1/visitantes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/vehiculos")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/reservas")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "USER")
    @DisplayName("GET /api/v1/vehiculos y /reservas devuelven 200 para rol USER (los necesita dashboard-user)")
    void sharedEndpointsAllowUserRole() throws Exception {
        var context = getContext();
        mockMvc.perform(get("/api/v1/vehiculos").with(securityContext(context))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/reservas").with(securityContext(context))).andExpect(status().isOk());
    }

    // El catalogo de visitantes paso a ser exclusivo del ADMIN: un visitante
    // solo reserva a su nombre, asi que no tiene por que ver quien mas esta
    // cargado. Sus propios datos los pide por /visitantes/me.
    @Test
    @WithMockUser(username = "visitante@test.com", authorities = "USER")
    @DisplayName("GET /api/v1/visitantes devuelve 403 para rol USER: el catalogo es del ADMIN")
    void visitantesCatalogIsAdminOnly() throws Exception {
        var context = getContext();
        mockMvc.perform(get("/api/v1/visitantes").with(securityContext(context)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("GET /api/v1/visitantes, /vehiculos y /reservas devuelven 200 para rol ADMIN (los necesita dashboard-admin)")
    void sharedEndpointsAllowAdminRole() throws Exception {
        var context = getContext();
        mockMvc.perform(get("/api/v1/visitantes").with(securityContext(context))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/vehiculos").with(securityContext(context))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/reservas").with(securityContext(context))).andExpect(status().isOk());
    }

    // ---- Endpoint público (usado por dashboard-user para elegir cochera) ----

    @Test
    @WithAnonymousUser
    @DisplayName("GET /api/v1/cocheras/disponibles es público")
    void cocherasDisponiblesIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/cocheras/disponibles").param("fecha", "2026-12-31"))
                .andExpect(status().isOk());
    }

    // ---- Endpoint exclusivo de dashboard-admin/usuarios ----

    @Test
    @WithAnonymousUser
    @DisplayName("GET /api/v1/usuarios devuelve 401 para anónimos")
    void usuariosRejectsAnonymousUsers() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "USER")
    @DisplayName("GET /api/v1/usuarios devuelve 403 para rol USER (esta pantalla es solo de ADMIN)")
    void usuariosRejectsUserRole() throws Exception {
        var context = getContext();
        mockMvc.perform(get("/api/v1/usuarios").with(securityContext(context)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("GET /api/v1/usuarios devuelve 200 para rol ADMIN")
    void usuariosAllowsAdminRole() throws Exception {
        var context = getContext();
        mockMvc.perform(get("/api/v1/usuarios").with(securityContext(context)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "USER")
    @DisplayName("El 403 devuelve el mismo formato JSON que el 401 (no un 404 ni un body vacío)")
    void forbiddenResponseHasConsistentJsonShape() throws Exception {
        var context = getContext();
        mockMvc.perform(get("/api/v1/usuarios").with(securityContext(context)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.path").value("/api/v1/usuarios"));
    }
}
